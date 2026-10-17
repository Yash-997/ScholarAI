package com.scholarship.service;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Reads dataset/source_registry.csv: the official (and partner) sources schemes are tracked against. */
@Component
public class SourceRegistry {

    private static final Logger log = LoggerFactory.getLogger(SourceRegistry.class);

    /**
     * @param schemeIds dataset ids (the {@code id} column of scholarships.csv) covered by this source
     * @param kind      PORTAL, SCHEME_PAGE, BRAND_PAGE or SITEMAP
     * @param adapter   HTML or B4S
     * @param trust     OFFICIAL, PARTNER or AGGREGATOR
     * @param itemMatch regex selecting the programme on a multi-programme page
     * @param autoApply whether dates read from this source may update records without a human
     * @param discover  whether links/sitemap entries on this source are followed to find scheme pages
     * @param linkFilter regex an href or anchor text must match to be treated as a scheme page
     */
    public record Entry(String sourceId, String organization, String portalName, String url, LocalDate accessDate,
                        List<String> schemeIds, String notes, String kind, String adapter, String trust,
                        String itemMatch, boolean autoApply, boolean discover, String linkFilter) {

        public Entry(String sourceId, String organization, String portalName, String url, LocalDate accessDate,
                     List<String> schemeIds, String notes) {
            this(sourceId, organization, portalName, url, accessDate, schemeIds, notes, "PORTAL", "HTML", "OFFICIAL", null, false, false, null);
        }
    }

    private final Path path;

    public SourceRegistry(@Value("${app.dataset.registry-path:${app.dataset.path:../dataset/scholarships.csv}}") String configured) {
        Path p = Path.of(configured);
        // The property may point at scholarships.csv (default) or directly at the registry file.
        this.path = p.getFileName().toString().equals("source_registry.csv") ? p : p.resolveSibling("source_registry.csv");
    }

    public List<Entry> load() {
        List<Entry> out = new ArrayList<>();
        if (!Files.isReadable(path)) {
            log.warn("Source registry {} not found; no sources will be monitored.", path.toAbsolutePath());
            return out;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).build().parse(reader)) {
            for (CSVRecord r : parser) {
                out.add(new Entry(r.get("source_id"), r.get("organization"), r.get("portal_name"), r.get("source_url"),
                        parseDate(opt(r, "access_date")),
                        Arrays.stream(opt(r, "schemes_covered").split(";")).map(String::trim).filter(s -> !s.isEmpty()).toList(),
                        opt(r, "verification_notes"),
                        orDefault(opt(r, "kind"), "PORTAL"), orDefault(opt(r, "adapter"), "HTML"), orDefault(opt(r, "trust"), "OFFICIAL"),
                        blankToNull(opt(r, "item_match")), Boolean.parseBoolean(opt(r, "auto_apply")),
                        Boolean.parseBoolean(opt(r, "discover")), blankToNull(opt(r, "link_filter"))));
            }
        } catch (Exception e) {
            log.error("Could not read source registry {}: {}", path, e.getMessage());
        }
        return out;
    }

    private static String opt(CSVRecord r, String col) {
        return r.isMapped(col) && r.isSet(col) ? r.get(col) : "";
    }

    private static String orDefault(String v, String d) {
        return v == null || v.isBlank() ? d : v.trim();
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static LocalDate parseDate(String v) {
        try {
            return v == null || v.isBlank() ? null : LocalDate.parse(v.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
