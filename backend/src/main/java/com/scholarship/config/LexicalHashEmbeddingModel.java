package com.scholarship.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Lexical (keyword-overlap) retrieval vectors: hashed bag of words with unigram and bigram features, stop-word removal
 * and L2 normalisation, stored in pgvector with cosine distance.
 *
 * This is <b>not</b> a semantic embedding. It matches shared words, not meaning: "girls" will not match "female" and a
 * paraphrase with no shared vocabulary scores near zero. It has no network dependency and is deterministic. A real
 * embedding model (any provider with an embeddings endpoint) should replace this bean when one is configured; the
 * vector_store dimension in application.yml must then be changed to match and the store re-indexed.
 */
@Component
@Primary
@ConditionalOnProperty(name = "app.embedding.provider", havingValue = "lexical", matchIfMissing = true)
public class LexicalHashEmbeddingModel extends AbstractEmbeddingModel {

    private static final Logger log = LoggerFactory.getLogger(LexicalHashEmbeddingModel.class);
    private static final int DIMENSIONS = 768;
    private static final double BIGRAM_WEIGHT = 0.5;

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "by", "for", "from", "has", "have", "how", "i", "in", "is", "it", "its",
            "me", "my", "of", "on", "or", "that", "the", "this", "to", "was", "what", "which", "who", "will", "with", "you",
            "your", "can", "do", "does", "any", "am", "about", "there", "if", "so", "we", "they", "them", "their");

    public LexicalHashEmbeddingModel() {
        log.info("Retrieval uses lexical hashed bag-of-words vectors ({} dims). This is keyword matching, not semantic search.", DIMENSIONS);
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    @Override
    public float[] embed(String text) {
        return vectorize(text);
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> results = new ArrayList<>(texts.size());
        for (String text : texts) {
            results.add(embed(text));
        }
        return results;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> instructions = request.getInstructions();
        List<Embedding> embeddings = new ArrayList<>(instructions.size());
        for (int i = 0; i < instructions.size(); i++) {
            embeddings.add(new Embedding(embed(instructions.get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    /** Lower-cased alphanumeric tokens without stop words; a trailing plural "s" is dropped so "schemes" matches "scheme". */
    public static List<String> tokens(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        for (String raw : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (raw.length() < 2 || STOP_WORDS.contains(raw)) continue;
            out.add(raw.length() > 3 && raw.endsWith("s") && !raw.endsWith("ss") ? raw.substring(0, raw.length() - 1) : raw);
        }
        return out;
    }

    static float[] vectorize(String text) {
        float[] vector = new float[DIMENSIONS];
        List<String> toks = tokens(text);
        if (toks.isEmpty()) {
            return vector;
        }
        Map<String, Double> features = new LinkedHashMap<>();
        for (int i = 0; i < toks.size(); i++) {
            features.merge(toks.get(i), 1.0, Double::sum);
            if (i + 1 < toks.size()) {
                features.merge(toks.get(i) + "_" + toks.get(i + 1), BIGRAM_WEIGHT, Double::sum);
            }
        }
        for (Map.Entry<String, Double> f : features.entrySet()) {
            int h = fnv1a(f.getKey());
            int index = Math.floorMod(h, DIMENSIONS);
            // The sign bit comes from a different part of the hash so colliding words tend to cancel instead of add.
            double sign = ((h >>> 16) & 1) == 0 ? 1.0 : -1.0;
            vector[index] += (float) (sign * (1.0 + Math.log(f.getValue())));
        }
        double norm = 0.0;
        for (float v : vector) norm += v * v;
        if (norm > 0) {
            float inv = (float) (1.0 / Math.sqrt(norm));
            for (int i = 0; i < DIMENSIONS; i++) vector[i] *= inv;
        }
        return vector;
    }

    private static int fnv1a(String s) {
        int h = 0x811c9dc5;
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            h ^= (b & 0xff);
            h *= 0x01000193;
        }
        return h;
    }
}
