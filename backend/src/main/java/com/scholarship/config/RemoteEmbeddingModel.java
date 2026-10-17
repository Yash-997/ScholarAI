package com.scholarship.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Genuine semantic embeddings from any OpenAI-compatible {@code /embeddings} endpoint. Enabled only when
 * {@code app.embedding.provider=openai-compatible} and a model is configured:
 *
 * <pre>
 *   EMBEDDING_PROVIDER=openai-compatible
 *   EMBEDDING_BASE_URL=https://api.openai.com/v1      (or any compatible server, including a local one)
 *   EMBEDDING_API_KEY=...                              (environment only; never committed)
 *   EMBEDDING_MODEL=text-embedding-3-small
 *   EMBEDDING_DIMENSIONS=1536                          (must equal the model's output size)
 * </pre>
 *
 * Groq does not serve text embeddings, so this is off by default and retrieval falls back to the lexical model. The
 * startup loader recreates the (derived, rebuildable) vector table when the dimension changes.
 */
@Component
@Primary
@ConditionalOnProperty(name = "app.embedding.provider", havingValue = "openai-compatible")
public class RemoteEmbeddingModel extends AbstractEmbeddingModel {

    private static final Logger log = LoggerFactory.getLogger(RemoteEmbeddingModel.class);
    private static final int BATCH = 32;

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final int dimensions;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public RemoteEmbeddingModel(@Value("${app.embedding.base-url:}") String baseUrl,
                                @Value("${app.embedding.api-key:}") String apiKey,
                                @Value("${app.embedding.model:}") String model,
                                @Value("${app.embedding.dimensions:768}") int dimensions) {
        if (baseUrl.isBlank() || model.isBlank()) {
            throw new IllegalStateException("app.embedding.provider=openai-compatible requires EMBEDDING_BASE_URL and EMBEDDING_MODEL");
        }
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.apiKey = apiKey;
        this.model = model;
        this.dimensions = dimensions;
        log.info("Retrieval uses remote embeddings: model {} ({} dims) at {}", model, dimensions, this.baseUrl);
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    @Override
    public float[] embed(String text) {
        return embedBatch(List.of(text == null ? "" : text)).get(0);
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> out = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i += BATCH) {
            out.addAll(embedBatch(texts.subList(i, Math.min(texts.size(), i + BATCH))));
        }
        return out;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<float[]> vectors = embed(request.getInstructions());
        List<Embedding> embeddings = new ArrayList<>(vectors.size());
        for (int i = 0; i < vectors.size(); i++) embeddings.add(new Embedding(vectors.get(i), i));
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    List<float[]> embedBatch(List<String> inputs) {
        Exception last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                String body = mapper.writeValueAsString(Map.of("model", model, "input", inputs));
                HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(baseUrl + "/embeddings")).timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
                if (!apiKey.isBlank()) rb.header("Authorization", "Bearer " + apiKey);
                HttpResponse<String> res = http.send(rb.build(), HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() == 429 || res.statusCode() >= 500) {
                    last = new IllegalStateException("HTTP " + res.statusCode());
                    Thread.sleep(500L * attempt);
                    continue;
                }
                if (res.statusCode() != 200) {
                    throw new IllegalStateException("Embedding endpoint answered HTTP " + res.statusCode());
                }
                JsonNode data = mapper.readTree(res.body()).path("data");
                if (!data.isArray() || data.size() != inputs.size()) {
                    throw new IllegalStateException("Embedding response did not contain one vector per input");
                }
                List<float[]> out = new ArrayList<>(inputs.size());
                for (JsonNode d : data) {
                    JsonNode e = d.path("embedding");
                    if (e.size() != dimensions) {
                        throw new IllegalStateException("Embedding has " + e.size() + " dimensions but EMBEDDING_DIMENSIONS is " + dimensions);
                    }
                    float[] v = new float[e.size()];
                    for (int i = 0; i < v.length; i++) v[i] = (float) e.get(i).asDouble();
                    out.add(v);
                }
                return out;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while embedding", e);
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                last = e;
            }
        }
        throw new IllegalStateException("Embedding request failed after retries: " + (last == null ? "" : last.getMessage()), last);
    }
}
