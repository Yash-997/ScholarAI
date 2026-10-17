package com.scholarship.config;

import com.scholarship.entity.DedupeKeys;
import com.scholarship.entity.ScholarshipEntity;
import com.scholarship.repository.ScholarshipRepository;
import com.scholarship.service.ScholarshipIngestionService;
import com.scholarship.service.ScholarshipUpsertService;
import com.scholarship.service.SourceMonitorService;
import com.scholarship.service.SourceRegistry;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;

/**
 * Loads dataset/scholarships.csv into PostgreSQL and rebuilds the vector_store index from it.
 * <ul>
 *   <li>Idempotent: rows are matched on the normalised-name dedupe key and skipped when their content hash is unchanged,
 *       so restarts do not overwrite deadlines an administrator verified in the meantime.</li>
 *   <li>Non-destructive: VERIFIED rows missing from the CSV are archived (ARCHIVED_LEGACY) and excluded from
 *       eligibility, never deleted.</li>
 *   <li>Honest dates: last_verified comes from the source registry's access date, not from the startup clock.</li>
 * </ul>
 */
@Component
public class ScholarshipDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ScholarshipDataLoader.class);
    public static final String ARCHIVED = "ARCHIVED_LEGACY";

    private final ScholarshipRepository scholarshipRepository;
    private final ScholarshipUpsertService upsertService;
    private final ScholarshipIngestionService scholarshipIngestionService;
    private final SourceMonitorService sourceMonitorService;
    private final SourceRegistry sourceRegistry;
    private final JdbcTemplate jdbcTemplate;
    private final Path datasetPath;
    private final boolean archiveMissing;

    @Value("${app.embedding.dimensions:768}")
    private int embeddingDimensions = 768;

    public ScholarshipDataLoader(
            ScholarshipRepository scholarshipRepository,
            ScholarshipUpsertService upsertService,
            ScholarshipIngestionService scholarshipIngestionService,
            SourceMonitorService sourceMonitorService,
            SourceRegistry sourceRegistry,
            JdbcTemplate jdbcTemplate,
            @Value("${app.dataset.path:../dataset/scholarships.csv}") String datasetPath,
            @Value("${app.dataset.archive-missing:true}") boolean archiveMissing
    ) {
        this.scholarshipRepository = scholarshipRepository;
        this.upsertService = upsertService;
        this.scholarshipIngestionService = scholarshipIngestionService;
        this.sourceMonitorService = sourceMonitorService;
        this.sourceRegistry = sourceRegistry;
        this.jdbcTemplate = jdbcTemplate;
        this.datasetPath = Path.of(datasetPath);
        this.archiveMissing = archiveMissing;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            backfillDedupeKeys();
            sourceMonitorService.syncRegistry();
            if (Files.isReadable(datasetPath)) {
                Set<String> loadedKeys = loadCsv();
                if (archiveMissing) archiveRowsNotInDataset(loadedKeys);
            } else {
                log.warn("Dataset {} not found; skipping seed. Set app.dataset.path to dataset/scholarships.csv.", datasetPath.toAbsolutePath());
            }
            reindexVectorStore();
        } catch (Exception e) {
            log.error("Dataset load failed: {}", e.getMessage(), e);
        }
    }

    /** Rows written before the dedupe_key column existed have no key; compute it so they are matched, not duplicated. */
    private void backfillDedupeKeys() {
        int n = 0;
        for (ScholarshipEntity e : scholarshipRepository.findAll()) {
            String key = DedupeKeys.forName(e.getName());
            if (key != null && !key.equals(e.getDedupeKey())) {
                e.setDedupeKey(key);
                scholarshipRepository.save(e);
                n++;
            }
        }
        if (n > 0) log.info("Backfilled dedupe keys on {} existing rows", n);
    }

    Set<String> loadCsv() throws IOException {
        Map<String, SourceRegistry.Entry> sourceByDatasetId = new HashMap<>();
        for (SourceRegistry.Entry src : sourceRegistry.load()) {
            for (String id : src.schemeIds()) sourceByDatasetId.put(id, src);
        }

        Set<String> keys = new HashSet<>();
        int created = 0, updated = 0, unchanged = 0;
        try (Reader reader = Files.newBufferedReader(datasetPath, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).build().parse(reader)) {
            for (CSVRecord r : parser) {
                String name = r.get("name");
                String key = DedupeKeys.forName(name);
                keys.add(key);
                SourceRegistry.Entry src = sourceByDatasetId.get(opt(r, "id"));

                String hash = rowHash(r, src);
                List<ScholarshipEntity> existing = scholarshipRepository.findByDedupeKey(key);
                if (!existing.isEmpty() && hash.equals(existing.get(0).getSeedHash()) && "VERIFIED".equals(existing.get(0).getVerificationStatus())) {
                    unchanged++;
                    continue;
                }

                String[] urls = r.get("urls").split("\\|");
                BigDecimal maxIncome = decimal(r.get("max_income"));
                ScholarshipEntity in = new ScholarshipEntity();
                in.setName(name);
                in.setProvider(r.get("provider"));
                in.setCourse(tokens(r.get("course")));
                in.setYearOfStudy(r.get("year"));
                in.setMinimumMarksOrCgpa(r.get("min_marks").isEmpty() ? null : Double.valueOf(r.get("min_marks")));
                in.setMaximumFamilyIncome(maxIncome);
                in.setState(tokens(r.get("state")));
                in.setCategoryCriteria(tokens(r.get("category")));
                in.setGenderRequirements(r.get("gender").isEmpty() ? "ALL" : r.get("gender"));
                in.setBenefits(r.get("benefits"));
                in.setDeadline(date(r.get("deadline")));
                in.setOpenDate(date(opt(r, "open_date")));
                // A date is only trusted when the dataset also says when it was verified.
                in.setDeadlineVerifiedOn(in.getDeadline() != null || in.getOpenDate() != null ? date(opt(r, "deadline_verified")) : null);
                in.setAcademicYear(blankToNull(opt(r, "academic_year")));
                if (in.getDeadline() != null || in.getOpenDate() != null) in.setDeadlineOrigin("DATASET");
                in.setRequiredDocuments(r.get("docs"));
                in.setApplicationUrl(urls[0].trim());
                in.setSourceUrl(urls.length > 1 ? urls[1].trim() : urls[0].trim());
                in.setSchemeType(schemeType(in.getProvider(), in.getState()));
                in.setEligibility(null);
                in.setVerificationStatus("VERIFIED");
                in.setSourceId(src != null ? src.sourceId() : null);
                in.setLastVerified(src != null ? src.accessDate() : null);
                in.setSeedHash(hash);

                ScholarshipUpsertService.Result res = upsertService.upsert(in, "DATASET");
                if (res.created()) created++; else updated++;
            }
        }
        log.info("Dataset {}: {} created, {} updated, {} unchanged", datasetPath.toAbsolutePath(), created, updated, unchanged);
        return keys;
    }

    private void archiveRowsNotInDataset(Set<String> loadedKeys) {
        List<ScholarshipEntity> stale = scholarshipRepository.findByVerificationStatus("VERIFIED").stream()
                .filter(s -> s.getDedupeKey() == null || !loadedKeys.contains(s.getDedupeKey()))
                .toList();
        for (ScholarshipEntity s : stale) {
            s.setVerificationStatus(ARCHIVED);
            s.setReviewReason("Not present in dataset/scholarships.csv; archived, not deleted. Set verification_status back to VERIFIED to restore.");
            scholarshipRepository.save(s);
        }
        if (!stale.isEmpty()) {
            log.info("Archived {} VERIFIED rows that are not in the dataset (kept in the database, excluded from results)", stale.size());
        }
    }

    /**
     * The vector table only holds derived data (rebuilt from the scholarships table at every start), so when the configured
     * embedding dimension differs from the table's, it is dropped and recreated rather than failing every insert.
     */
    private void ensureVectorDimensions() {
        try {
            Integer current = jdbcTemplate.queryForObject(
                    "SELECT atttypmod FROM pg_attribute WHERE attrelid = to_regclass('vector_store') AND attname = 'embedding'", Integer.class);
            if (current != null && current > 0 && current != embeddingDimensions) {
                log.warn("vector_store has {} dimensions but {} are configured; recreating the (derived) table", current, embeddingDimensions);
                jdbcTemplate.execute("DROP TABLE IF EXISTS vector_store");
                jdbcTemplate.execute("CREATE TABLE vector_store (id uuid DEFAULT uuid_generate_v4() PRIMARY KEY, content text, metadata json, embedding vector("
                        + embeddingDimensions + "))");
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS spring_ai_vector_index ON vector_store USING hnsw (embedding vector_cosine_ops)");
            }
        } catch (Exception e) {
            log.debug("Could not inspect vector_store dimensions: {}", e.getMessage());
        }
    }

    private void reindexVectorStore() {
        ensureVectorDimensions();
        try {
            jdbcTemplate.execute("DROP TABLE IF EXISTS scholarship_chunks");
            jdbcTemplate.execute("DELETE FROM vector_store");
        } catch (Exception e) {
            log.warn("Could not clear vector_store before reindex: {}", e.getMessage());
        }
        int indexed = 0;
        for (ScholarshipEntity entity : scholarshipRepository.findByVerificationStatus("VERIFIED")) {
            scholarshipIngestionService.indexSingleScholarship(entity);
            indexed++;
        }
        log.info("vector_store rebuilt for {} scholarships", indexed);
    }

    private static String rowHash(CSVRecord r, SourceRegistry.Entry src) {
        StringBuilder sb = new StringBuilder();
        r.toMap().forEach((k, v) -> sb.append(k).append('=').append(v).append('\u001f'));
        if (src != null) sb.append(src.sourceId()).append(src.accessDate());
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String opt(CSVRecord r, String col) {
        return r.isMapped(col) && r.isSet(col) ? r.get(col) : "";
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static LocalDate date(String v) {
        return v == null || v.isBlank() ? null : LocalDate.parse(v.trim());
    }

    /** Classification used by the Explore view's type filter; the dataset has no scheme-type column. */
    private static String schemeType(String provider, String state) {
        String p = provider.toLowerCase();
        if (p.contains("ministry") || p.contains("aicte")) {
            return "Central Government";
        }
        return "ALL".equals(state) ? "Corporate CSR" : "State Government";
    }

    private static BigDecimal decimal(String v) {
        return v.isEmpty() ? null : new BigDecimal(v);
    }

    /** Normalises "a; b ;c" to "a;b;c"; empty means unrestricted. */
    private static String tokens(String v) {
        if (v.isEmpty()) return "ALL";
        return String.join(";", Arrays.stream(v.split(";")).map(String::trim).filter(t -> !t.isEmpty()).toList());
    }
}
