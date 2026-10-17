package com.scholarship.service;

import com.scholarship.entity.ScholarshipEntity;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static com.scholarship.service.TestSchemes.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class EligibilityEvaluatorTest {

    private static boolean eligible(ScholarshipEntity s, EligibilityEvaluator.Profile p) {
        return EligibilityEvaluator.isEligible(s, p);
    }

    // ---- income boundary: ceiling is inclusive -------------------------------------------------

    @Test
    void incomeAtCeilingIsEligible_oneRupeeAboveIsNot() {
        ScholarshipEntity s = maxIncome(scheme(1, "A"), 250000);
        assertTrue(eligible(s, profile(250000L, null, null, null, null, null)));
        assertTrue(eligible(s, profile(249999L, null, null, null, null, null)));
        assertFalse(eligible(s, profile(250001L, null, null, null, null, null)));
    }

    // ---- marks boundary: minimum is inclusive --------------------------------------------------

    @Test
    void marksAtMinimumIsEligible_belowIsNot() {
        ScholarshipEntity s = minMarks(scheme(1, "A"), 80);
        assertTrue(eligible(s, profile(null, 80.0, null, null, null, null)));
        assertTrue(eligible(s, profile(null, 100.0, null, null, null, null)));
        assertFalse(eligible(s, profile(null, 79.5, null, null, null, null)));
        assertFalse(eligible(s, profile(null, 35.0, null, null, null, null)));
    }

    @Test
    void schemeWithoutMinimumAcceptsAnyMarks_andProfileWithoutMarksIsNotFiltered() {
        assertTrue(eligible(scheme(1, "A"), profile(null, 35.0, null, null, null, null)));
        assertTrue(eligible(minMarks(scheme(2, "B"), 90), profile(null, null, null, null, null, null)));
    }

    // ---- categorical filters -------------------------------------------------------------------

    @Test
    void stateMatchesListedStatesAndWildcards() {
        ScholarshipEntity mh = scheme(1, "MH");
        mh.setState("Maharashtra");
        ScholarshipEntity two = scheme(2, "Two");
        two.setState("Jammu and Kashmir;Ladakh");
        ScholarshipEntity all = scheme(3, "All");
        all.setState("All India");
        assertTrue(eligible(mh, profile(null, null, "Maharashtra", null, null, null)));
        assertTrue(eligible(mh, profile(null, null, "maharashtra", null, null, null)));
        assertFalse(eligible(mh, profile(null, null, "Karnataka", null, null, null)));
        assertTrue(eligible(two, profile(null, null, "Ladakh", null, null, null)));
        assertFalse(eligible(two, profile(null, null, "Delhi", null, null, null)));
        assertTrue(eligible(all, profile(null, null, "Karnataka", null, null, null)));
        assertTrue(eligible(mh, profile(null, null, "All India", null, null, null)), "All India means no state filter");
    }

    @Test
    void categoryMatchingUnderstandsSynonymsAndSeparators() {
        ScholarshipEntity open = scheme(1, "Open");
        open.setCategoryCriteria("GEN;EWS");
        ScholarshipEntity legacy = scheme(2, "Legacy");
        legacy.setCategoryCriteria("General, Open / SBC");
        ScholarshipEntity sc = scheme(3, "SC");
        sc.setCategoryCriteria("SC");
        assertTrue(eligible(open, profile(null, null, null, "General", null, null)));
        assertTrue(eligible(open, profile(null, null, null, "EWS", null, null)));
        assertFalse(eligible(open, profile(null, null, null, "OBC", null, null)));
        assertTrue(eligible(legacy, profile(null, null, null, "Open", null, null)));
        assertTrue(eligible(legacy, profile(null, null, null, "VJNT / DNT / SBC", null, null)));
        assertTrue(eligible(sc, profile(null, null, null, "sc", null, null)));
        assertFalse(eligible(sc, profile(null, null, null, "ST", null, null)));
    }

    @Test
    void courseAndGenderFilters() {
        ScholarshipEntity eng = scheme(1, "Eng");
        eng.setCourse("Engineering;Diploma");
        ScholarshipEntity girls = scheme(2, "Girls");
        girls.setGenderRequirements("Female");
        assertTrue(eligible(eng, profile(null, null, null, null, "B.Tech / Engineering", null)));
        assertTrue(eligible(eng, profile(null, null, null, null, "Diploma in Engineering", null)));
        assertFalse(eligible(eng, profile(null, null, null, null, "MBBS / Healthcare", null)));
        assertTrue(eligible(girls, profile(null, null, null, null, null, "Female")));
        assertFalse(eligible(girls, profile(null, null, null, null, null, "Male")));
        assertTrue(eligible(girls, profile(null, null, null, null, null, "All")), "Any gender means no gender filter");
    }

    @Test
    void failuresExplainWhyAStudentIsRejected() {
        ScholarshipEntity s = maxIncome(minMarks(scheme(1, "A"), 80), 250000);
        s.setCategoryCriteria("SC");
        List<String> f = EligibilityEvaluator.failures(s, profile(300000L, 70.0, null, "OBC", null, null));
        assertEquals(3, f.size());
    }

    // ---- the reported bug: lowering marks must never ADD a scheme ------------------------------

    @Test
    void loweringMarks_neverAddsSchemes_syntheticSet() {
        List<ScholarshipEntity> schemes = new ArrayList<>();
        for (int i = 0; i < 12; i++) schemes.add(minMarks(scheme(i, "S" + i), 40 + i * 5));
        schemes.add(scheme(99, "no minimum"));
        assertMonotonicInMarks(schemes, profile(300000L, null, "Maharashtra", "General", "Engineering", "Female"));
    }

    @Test
    void loweringMarks_neverAddsSchemes_realDataset() throws Exception {
        List<ScholarshipEntity> schemes = loadDataset();
        assumeTrue(!schemes.isEmpty(), "dataset/scholarships.csv not found from the test working directory");
        String[] states = {"Maharashtra", "Karnataka", "Uttar Pradesh", "West Bengal", "Ladakh", "All India"};
        String[] categories = {"General", "EWS", "OBC", "SC", "ST", "VJNT", "Minority", "PWD", "Orphan"};
        String[] genders = {"All", "Female", "Male"};
        String[] courses = {"Engineering", "Diploma", "Medical", "Pharmacy", "Degree"};
        for (String st : states) for (String cat : categories) for (String g : genders) for (String c : courses) {
            assertMonotonicInMarks(schemes, profile(300000L, null, st, cat, c, g));
        }
    }

    private static void assertMonotonicInMarks(List<ScholarshipEntity> schemes, EligibilityEvaluator.Profile base) {
        Set<Long> previous = null;
        for (double marks = 100; marks >= 0; marks -= 0.5) {
            EligibilityEvaluator.Profile p = new EligibilityEvaluator.Profile(base.income(), marks, base.state(), base.category(), base.course(), base.gender());
            Set<Long> now = new TreeSet<>();
            for (ScholarshipEntity s : schemes) if (EligibilityEvaluator.isEligible(s, p)) now.add(s.getId());
            if (previous != null) {
                assertTrue(previous.containsAll(now), "Lowering marks to " + marks + " added schemes " + diff(now, previous) + " for " + base);
            }
            previous = now;
        }
    }

    private static Set<Long> diff(Set<Long> a, Set<Long> b) {
        Set<Long> d = new TreeSet<>(a);
        d.removeAll(b);
        return d;
    }

    /** Loads the dataset the way the loader does, without a database. Empty when the file is not reachable. */
    static List<ScholarshipEntity> loadDataset() throws Exception {
        for (String candidate : new String[]{"../dataset/scholarships.csv", "dataset/scholarships.csv"}) {
            Path path = Path.of(candidate);
            if (!Files.isReadable(path)) continue;
            List<ScholarshipEntity> out = new ArrayList<>();
            try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8);
                 CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).build().parse(r)) {
                for (CSVRecord rec : parser) {
                    ScholarshipEntity s = scheme(Long.parseLong(rec.get("id")), rec.get("name"));
                    s.setCourse(tokens(rec.get("course")));
                    s.setState(tokens(rec.get("state")));
                    s.setCategoryCriteria(tokens(rec.get("category")));
                    s.setGenderRequirements(rec.get("gender").isEmpty() ? "ALL" : rec.get("gender"));
                    if (!rec.get("min_marks").isEmpty()) s.setMinimumMarksOrCgpa(Double.valueOf(rec.get("min_marks")));
                    if (!rec.get("max_income").isEmpty()) s.setMaximumFamilyIncome(new BigDecimal(rec.get("max_income")));
                    out.add(s);
                }
            }
            return out;
        }
        return List.of();
    }

    private static String tokens(String v) {
        return v.isEmpty() ? "ALL" : v;
    }
}
