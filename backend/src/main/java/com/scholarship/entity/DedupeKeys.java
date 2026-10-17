package com.scholarship.entity;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Canonical key for a scheme name. Two rows with the same key are the same scheme, so ingestion upserts instead of inserting.
 * Case, punctuation, diacritics, and common transliteration variants (yojna/yojana) do not affect the key.
 */
public final class DedupeKeys {

    private DedupeKeys() {
    }

    public static String forName(String name) {
        if (name == null) return null;
        String s = Normalizer.normalize(name, Normalizer.Form.NFKD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        s = s.replace("yojna", "yojana");
        s = s.replaceAll("[^a-z0-9]+", "");
        return s.isEmpty() ? null : s;
    }
}
