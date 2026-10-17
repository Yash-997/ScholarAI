package com.scholarship.service;

import com.scholarship.entity.DedupeKeys;
import com.scholarship.entity.DeadlineHistoryEntity;
import com.scholarship.entity.ScholarshipEntity;
import com.scholarship.entity.SourceSnapshotEntity;
import com.scholarship.repository.DeadlineHistoryRepository;
import com.scholarship.repository.ScholarshipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns what a source page says into database changes, under rules that keep wrong data out:
 * <ul>
 *   <li>Only sources marked auto_apply (a structured adapter or an explicit opt-in) and not AGGREGATOR-trust may change a
 *       VERIFIED record. Others produce PROPOSED history rows and a review flag.</li>
 *   <li>A date a reviewer verified (origin ADMIN) is never overwritten automatically; a differing source date is
 *       proposed and flagged.</li>
 *   <li>Conflicting dates for the same programme and cycle, an ambiguous page, or a closed notice that contradicts a
 *       future deadline change nothing and flag the scheme. The last valid data is preserved.</li>
 *   <li>Cycles older than the one on record are ignored. Every deadline change is written to the history table with the
 *       wording or field it came from.</li>
 *   <li>Programmes found on a partner page that are not in the dataset become PENDING_REVIEW records. They never enter
 *       eligibility results until a person approves them.</li>
 * </ul>
 * All changes for one source happen in one transaction, so a failure leaves the previous state intact. Re-running with an
 * unchanged page changes nothing except the "last verified" timestamps.
 */
@Service
public class SchemeRefreshService {

    private static final Logger log = LoggerFactory.getLogger(SchemeRefreshService.class);
    static final int MAX_CREATED_PER_SOURCE = 30;
    private static final Pattern YEAR_TOKEN = Pattern.compile("\\b20\\d\\d\\s*[\\u2013\\-/]?\\s*(?:20)?\\d{0,2}\\b");
    private static final Pattern INCOME = Pattern.compile(
            "(?:not\\s+(?:exceed|be\\s+more\\s+than)|below|up\\s+to|under|less\\s+than|maximum(?:\\s+of)?)[^.]{0,50}?(?:INR|Rs\\.?|\\u20B9)\\s*([\\d,]+(?:\\.\\d+)?)\\s*(lakhs?|lacs?)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MARKS = Pattern.compile("(?:at\\s+least|minimum(?:\\s+of)?|scored|secured)[^.]{0,40}?(\\d{2}(?:\\.\\d+)?)\\s*%", Pattern.CASE_INSENSITIVE);

    /** @param reindex ids of VERIFIED records whose searchable text changed and must be re-indexed after commit */
    public record Outcome(int updated, int created, int flagged, List<String> notes, List<Long> reindex) {
    }

    private final ScholarshipRepository schemes;
    private final DeadlineHistoryRepository history;
    private final TransactionTemplate tx;
    private final Clock clock;

    public SchemeRefreshService(ScholarshipRepository schemes, DeadlineHistoryRepository history,
                                PlatformTransactionManager txManager, Clock clock) {
        this.schemes = schemes;
        this.history = history;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
    }

    public Outcome apply(SourceSnapshotEntity src, List<SchemeCandidate> candidates, PageFetcher.Page page) {
        return tx.execute(status -> doApply(src, candidates));
    }

    private Outcome doApply(SourceSnapshotEntity src, List<SchemeCandidate> candidates) {
        LocalDate today = LocalDate.now(clock);
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<String> notes = new ArrayList<>();
        Set<Long> reindex = new LinkedHashSet<>();
        int[] counts = new int[3]; // updated, created, flagged

        List<ScholarshipEntity> mine = schemes.findBySourceId(src.getSourceId());
        Pattern item = src.getItemMatch() != null && !"SITEMAP".equals(src.getKind()) ? Pattern.compile(src.getItemMatch(), Pattern.CASE_INSENSITIVE) : null;

        // Group candidates by the record they describe.
        Map<String, List<SchemeCandidate>> groups = new LinkedHashMap<>();
        Map<String, ScholarshipEntity> targets = new HashMap<>();
        for (SchemeCandidate c : candidates) {
            if (c.name() == null || c.name().isBlank()) continue;
            if (item != null && !item.matcher(c.name()).find()) continue;
            ScholarshipEntity target = null;
            if (item != null) {
                if (mine.size() == 1) {
                    target = mine.get(0);
                } else if (mine.size() > 1) {
                    for (ScholarshipEntity s : mine) flag(s, "Source " + src.getSourceId() + " maps to several records; cannot tell which one \"" + c.name() + "\" belongs to.", counts);
                    notes.add("ambiguous mapping for " + c.name());
                    continue;
                }
            } else if ("SCHEME_PAGE".equals(src.getKind()) && !src.isDiscovered() && mine.size() == 1) {
                target = mine.get(0);   // a curated page dedicated to exactly one scheme
            } else {
                target = findTarget(c, mine);
            }
            String key = target != null ? "id:" + target.getId() : (src.isDiscovered() ? "new:" + stem(c.name()) : null);
            if (key == null) continue;   // a page of a curated source mentions a programme we do not track: ignore it
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(c);
            if (target != null) targets.put(key, target);
        }

        for (Map.Entry<String, List<SchemeCandidate>> g : groups.entrySet()) {
            ScholarshipEntity target = targets.get(g.getKey());
            int newest = g.getValue().stream().mapToInt(SchemeCandidate::academicYearStart).max().orElse(0);
            List<SchemeCandidate> latest = g.getValue().stream().filter(c -> c.academicYearStart() == newest).toList();
            Set<LocalDate> distinct = new HashSet<>();
            latest.forEach(c -> distinct.add(c.deadline()));
            if (distinct.size() > 1) {
                String why = "Source " + src.getSourceId() + " lists conflicting deadlines for the same programme and cycle: "
                        + latest.stream().map(c -> String.valueOf(c.deadline())).distinct().toList() + ". Previous data kept; verify on the official page.";
                notes.add("CONFLICT " + (target != null ? target.getName() : g.getKey()) + ": " + why);
                if (target != null) flag(target, why, counts);
                continue;
            }
            SchemeCandidate c = latest.get(0);
            if (target != null) {
                applyToExisting(src, target, c, today, now, notes, reindex, counts);
            } else if (counts[1] < MAX_CREATED_PER_SOURCE) {
                createPending(src, c, today, now, notes, counts);
            }
        }
        if (!reindex.isEmpty() || counts[1] > 0 || counts[0] > 0) {
            log.info("Source {}: {} updated, {} created, {} flagged", src.getSourceId(), counts[0], counts[1], counts[2]);
        }
        return new Outcome(counts[0], counts[1], counts[2], notes, new ArrayList<>(reindex));
    }

    private ScholarshipEntity findTarget(SchemeCandidate c, List<ScholarshipEntity> mine) {
        if (c.externalKey() != null) {
            for (ScholarshipEntity s : mine) if (c.externalKey().equals(s.getExternalKey())) return s;
            List<ScholarshipEntity> byKey = schemes.findByExternalKey(c.externalKey());
            if (!byKey.isEmpty()) return byKey.get(0);
        }
        String stem = stem(c.name());
        for (ScholarshipEntity s : mine) if (stem.equals(stem(s.getName()))) return s;
        return null;
    }

    private void applyToExisting(SourceSnapshotEntity src, ScholarshipEntity s, SchemeCandidate c, LocalDate today, OffsetDateTime now,
                                 List<String> notes, Set<Long> reindex, int[] counts) {
        boolean staging = !"VERIFIED".equals(s.getVerificationStatus());
        boolean trusted = src.isAutoApply() && !"AGGREGATOR".equals(src.getTrust());
        int recordYear = academicYearStart(s.getAcademicYear());
        if (c.academicYearStart() != 0 && recordYear != 0 && c.academicYearStart() < recordYear) {
            notes.add("ignored older cycle " + c.academicYear() + " for " + s.getName());
            return;
        }
        if (c.evidence() != null && c.evidence().startsWith("AMBIGUOUS")) {
            flag(s, "Source " + src.getSourceId() + " page shows several possible dates and none was applied. " + c.evidence() + ". Verify at " + src.getUrl() + ".", counts);
            notes.add("ambiguous page for " + s.getName());
            return;
        }
        boolean differs = c.deadline() != null && !c.deadline().equals(s.getDeadline());
        if (!staging && (!trusted || ("ADMIN".equals(s.getDeadlineOrigin()) && s.getDeadlineVerifiedOn() != null && differs))) {
            if (differs) {
                proposeOnce(s, c, src);
                flag(s, "Source " + src.getSourceId() + " shows a deadline of " + c.deadline() + " but " + (trusted ? "a reviewer verified " + s.getDeadline() : "this source is not allowed to update records automatically")
                        + ". Verify at " + src.getUrl() + ".", counts);
                notes.add("proposed (not applied) " + c.deadline() + " for " + s.getName());
            }
            return;
        }
        if (c.deadline() == null && c.windowState() != null) {
            boolean futureDeadline = s.getDeadline() != null && !today.isAfter(s.getDeadline()) && s.getDeadlineVerifiedOn() != null;
            if (SchemeStatusCalculator.CLOSED_NOTICE.equals(c.windowState()) && futureDeadline) {
                flag(s, "The official page says applications are closed, but a verified deadline of " + s.getDeadline() + " is on record. Nothing was changed; verify at " + src.getUrl() + ".", counts);
                notes.add("closed-notice conflicts with deadline for " + s.getName());
                return;
            }
        } else if (c.deadline() == null) {
            // the page says nothing about dates: leave the record exactly as it is
            if (s.getWindowState() != null && c.windowState() == null && trusted) {
                flag(s, "The official page no longer states that applications are " + (SchemeStatusCalculator.ROLLING_OPEN.equals(s.getWindowState()) ? "rolling" : "closed")
                        + ". Previous status kept; verify at " + src.getUrl() + ".", counts);
            }
            return;
        }

        LocalDate previousDeadline = s.getDeadline();
        boolean changed = false;
        if (c.deadline() != null) {
            changed |= !Objects.equals(s.getDeadline(), c.deadline()) || !Objects.equals(s.getOpenDate(), c.openDate())
                    || !Objects.equals(s.getAcademicYear(), c.academicYear() != null ? c.academicYear() : s.getAcademicYear());
            s.setDeadline(c.deadline());
            s.setOpenDate(c.openDate());
            if (c.academicYear() != null) s.setAcademicYear(c.academicYear());
            if (s.getWindowState() != null) {
                s.setWindowState(null);
                s.setWindowEvidence(null);
                changed = true;
            }
        } else {
            changed |= !Objects.equals(s.getWindowState(), c.windowState());
            s.setWindowState(c.windowState());
            s.setWindowEvidence(c.windowEvidence());
        }
        s.setDeadlineVerifiedOn(today);
        s.setDeadlineSourceUrl(c.pageUrl());
        s.setDeadlineFetchedAt(now);
        s.setDeadlineOrigin("SOURCE_AUTO");
        s.setLastVerified(today);
        s.setLastChecked(now);
        if (c.externalKey() != null) s.setExternalKey(c.externalKey());
        if (s.isNeedsReview() && s.getReviewReason() != null
                && (s.getReviewReason().startsWith("Source " + src.getSourceId()) || s.getReviewReason().startsWith("Official source " + src.getSourceId()))) {
            s.setNeedsReview(false);
            s.setReviewReason(null);
        }
        if (staging) fillContent(s, c);
        schemes.save(s);

        if (c.deadline() != null && !Objects.equals(previousDeadline, c.deadline())) {
            history.save(new DeadlineHistoryEntity(s.getId(), previousDeadline, c.deadline(), s.getAcademicYear(), DeadlineHistoryEntity.APPLIED,
                    "SOURCE_AUTO", (c.evidence() == null ? "" : c.evidence() + " | ") + c.pageUrl()));
            notes.add("deadline " + previousDeadline + " -> " + c.deadline() + " for " + s.getName());
        }
        if (changed) {
            counts[0]++;
            if (!staging) reindex.add(s.getId());
        }
    }

    private void createPending(SourceSnapshotEntity src, SchemeCandidate c, LocalDate today, OffsetDateTime now, List<String> notes, int[] counts) {
        if (c.deadline() != null && c.deadline().isBefore(today.minusDays(30))) return;   // an old cycle is not worth staging
        String key = DedupeKeys.forName(c.name());
        if (key == null || !schemes.findByDedupeKey(key).isEmpty()) return;
        ScholarshipEntity s = new ScholarshipEntity();
        s.setName(c.name());
        s.setProvider(src.getOrganization());
        s.setSchemeType("Corporate CSR");
        s.setVerificationStatus("PENDING_REVIEW");
        s.setSourceId(src.getSourceId());
        s.setSourceUrl(c.pageUrl());
        s.setExternalKey(c.externalKey());
        s.setDeadline(c.deadline());
        s.setOpenDate(c.openDate());
        s.setAcademicYear(c.academicYear());
        s.setDeadlineVerifiedOn(c.deadline() != null || c.openDate() != null ? today : null);
        s.setDeadlineSourceUrl(c.pageUrl());
        s.setDeadlineFetchedAt(now);
        s.setDeadlineOrigin("SOURCE_AUTO");
        s.setLastChecked(now);
        s.setNeedsReview(true);
        s.setReviewReason("Discovered automatically on " + today + " from " + c.pageUrl()
                + ". Eligibility was parsed from free text and is NOT applied to results until a reviewer approves this record.");
        fillContent(s, c);
        schemes.save(s);
        if (c.deadline() != null) {
            history.save(new DeadlineHistoryEntity(s.getId(), null, c.deadline(), c.academicYear(), DeadlineHistoryEntity.APPLIED,
                    "SOURCE_AUTO", (c.evidence() == null ? "" : c.evidence() + " | ") + c.pageUrl()));
        }
        counts[1]++;
        notes.add("discovered " + c.name());
    }

    private static void fillContent(ScholarshipEntity s, SchemeCandidate c) {
        if (c.eligibilityText() != null && !c.eligibilityText().isBlank()) {
            s.setEligibility(clip(c.eligibilityText(), 1500));
            BigDecimal income = parseIncome(c.eligibilityText());
            if (income != null) {
                s.setMaximumFamilyIncome(income);
                s.setIncomeLimit(income);
            }
            Double marks = parseMarks(c.eligibilityText());
            if (marks != null) s.setMinimumMarksOrCgpa(marks);
        }
        if (c.benefits() != null && !c.benefits().isBlank()) s.setBenefits(clip(c.benefits(), 800));
        if (c.documents() != null && !c.documents().isBlank()) s.setRequiredDocuments(clip(c.documents(), 1500));
        s.setApplicationUrl(c.applicationUrl() != null ? c.applicationUrl() : c.pageUrl());
    }

    static BigDecimal parseIncome(String text) {
        Matcher m = INCOME.matcher(text);
        if (!m.find()) return null;
        try {
            BigDecimal v = new BigDecimal(m.group(1).replace(",", ""));
            return m.group(2) != null ? v.multiply(BigDecimal.valueOf(100000)) : v;
        } catch (Exception e) {
            return null;
        }
    }

    static Double parseMarks(String text) {
        Matcher m = MARKS.matcher(text);
        if (!m.find()) return null;
        double v = Double.parseDouble(m.group(1));
        return v >= 30 && v <= 100 ? v : null;
    }

    private void proposeOnce(ScholarshipEntity s, SchemeCandidate c, SourceSnapshotEntity src) {
        if (history.existsByScholarshipIdAndDeadlineAndState(s.getId(), c.deadline(), DeadlineHistoryEntity.PROPOSED)) return;
        history.save(new DeadlineHistoryEntity(s.getId(), s.getDeadline(), c.deadline(), c.academicYear(), DeadlineHistoryEntity.PROPOSED,
                "SOURCE_CHECK", (c.evidence() == null ? "" : c.evidence() + " | ") + src.getUrl()));
    }

    private void flag(ScholarshipEntity s, String reason, int[] counts) {
        s.setNeedsReview(true);
        s.setReviewReason(reason);
        schemes.save(s);
        counts[2]++;
    }

    /** Name without cycle years or the Program/Programme spelling, so "X 2026-27" and "X 2025-26" are the same programme. */
    static String stem(String name) {
        if (name == null) return "";
        return YEAR_TOKEN.matcher(name.toLowerCase(Locale.ROOT)).replaceAll(" ").replace("programme", "program").replaceAll("[^a-z]+", "");
    }

    static int academicYearStart(String academicYear) {
        if (academicYear == null) return 0;
        Matcher m = Pattern.compile("(20\\d\\d)").matcher(academicYear);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private static String clip(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
