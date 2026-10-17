package com.scholarship.service;

import com.scholarship.entity.DeadlineHistoryEntity;
import com.scholarship.entity.ScholarshipEntity;
import com.scholarship.entity.SourceSnapshotEntity;
import com.scholarship.repository.DeadlineHistoryRepository;
import com.scholarship.repository.ScholarshipRepository;
import com.scholarship.repository.SourceSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Checks the registered sources, discovers scheme pages, and refreshes records.
 *
 * Safety rules, enforced here and in {@link SchemeRefreshService}, and covered by tests:
 * <ul>
 *   <li>A failed, blocked, empty or unreadable fetch only updates fetch bookkeeping. It never changes a scheme's dates or
 *       status and is never treated as evidence that a scheme closed. The last valid data is preserved.</li>
 *   <li>Structured partner pages and explicitly opted-in official pages may update dates (with history and provenance).
 *       Everything else flags the scheme for review and records PROPOSED dates only.</li>
 *   <li>Only one run executes at a time; overlapping triggers are skipped, so the job is safe to schedule and to call by hand.</li>
 * </ul>
 */
@Service
public class SourceMonitorService {

    private static final Logger log = LoggerFactory.getLogger(SourceMonitorService.class);
    static final int UNREACHABLE_FLAG_THRESHOLD = 3;

    /** Sentences about money, dates or criteria. Navigation, counters and timestamps are ignored by change detection. */
    private static final Pattern RELEVANT = Pattern.compile(
            "\\b(?:deadline|last date|closing|apply|application|eligib|income|scholarship|amount|rs|lakh|marks|percent|document|renew|fellowship)|₹|%",
            Pattern.CASE_INSENSITIVE);

    public enum Outcome { BASELINE, UNCHANGED, CHANGED, UPDATED, DISCOVERED, FETCH_FAILED, UNREADABLE, SKIPPED }

    public record CheckResult(String sourceId, String url, Outcome outcome, Integer httpStatus, String detail) {
    }

    private final SourceSnapshotRepository snapshots;
    private final ScholarshipRepository schemes;
    private final DeadlineHistoryRepository history;
    private final SourceRegistry registry;
    private final PageFetcher fetcher;
    private final Clock clock;
    private final SchemeRefreshService refresh;
    private final SourceDiscoveryService discovery;
    private final ScholarshipIngestionService indexer;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public SourceMonitorService(SourceSnapshotRepository snapshots, ScholarshipRepository schemes,
                                DeadlineHistoryRepository history, SourceRegistry registry, PageFetcher fetcher, Clock clock,
                                SchemeRefreshService refresh, SourceDiscoveryService discovery, ScholarshipIngestionService indexer) {
        this.snapshots = snapshots;
        this.schemes = schemes;
        this.history = history;
        this.registry = registry;
        this.fetcher = fetcher;
        this.clock = clock;
        this.refresh = refresh;
        this.discovery = discovery;
        this.indexer = indexer;
    }

    /** Mirrors the registry file into source_snapshots without disturbing fetch state or discovered rows. */
    public void syncRegistry() {
        for (SourceRegistry.Entry e : registry.load()) {
            SourceSnapshotEntity snap = snapshots.findById(e.sourceId())
                    .orElseGet(() -> new SourceSnapshotEntity(e.sourceId(), e.url(), e.organization(), e.portalName()));
            snap.setUrl(e.url());
            snap.setOrganization(e.organization());
            snap.setPortalName(e.portalName());
            snap.setKind(e.kind());
            snap.setAdapter(e.adapter());
            snap.setTrust(e.trust());
            snap.setItemMatch(e.itemMatch());
            snap.setAutoApply(e.autoApply());
            snap.setDiscoverLinks(e.discover());
            snap.setLinkFilter(e.linkFilter());
            snapshots.save(snap);
        }
    }

    public List<SourceSnapshotEntity> snapshots() {
        return snapshots.findAll();
    }

    public boolean isRunning() {
        return running.get();
    }

    /** Runs every source once. Returns a single SKIPPED result when another run is already in progress. */
    public List<CheckResult> checkAll() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Source check requested while another run is in progress; skipping");
            return List.of(new CheckResult("-", "-", Outcome.SKIPPED, null, "A source check is already running."));
        }
        try {
            syncRegistry();
            List<CheckResult> results = new ArrayList<>();
            // Discovery first, so pages it registers are checked in the same run.
            for (SourceSnapshotEntity snap : snapshots.findAll()) {
                if ("SITEMAP".equals(snap.getKind()) || snap.isDiscoverLinks()) results.add(safeCheck(snap));
            }
            for (SourceSnapshotEntity snap : snapshots.findAll()) {
                if (!("SITEMAP".equals(snap.getKind()) || snap.isDiscoverLinks())) results.add(safeCheck(snap));
            }
            log.info("Source check finished: {}", results.stream().map(r -> r.sourceId() + "=" + r.outcome()).toList());
            return results;
        } finally {
            running.set(false);
        }
    }

    private CheckResult safeCheck(SourceSnapshotEntity snap) {
        try {
            return checkOne(snap);
        } catch (Exception e) {
            log.error("Source check for {} crashed; scheme data left untouched: {}", snap.getSourceId(), e.getMessage(), e);
            return new CheckResult(snap.getSourceId(), snap.getUrl(), Outcome.FETCH_FAILED, null, "internal error: " + e.getMessage());
        }
    }

    CheckResult checkOne(SourceSnapshotEntity snap) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        LocalDate today = LocalDate.now(clock);
        snap.setLastChecked(now);

        PageFetcher.Page page = fetcher.fetch(snap.getUrl());
        snap.setLastHttpStatus(page.status());

        if (!page.ok()) {
            snap.setConsecutiveFailures(snap.getConsecutiveFailures() + 1);
            snap.setLastError(page.error());
            snapshots.save(snap);
            if (snap.getConsecutiveFailures() >= UNREACHABLE_FLAG_THRESHOLD) {
                flag(snap, "Official source " + snap.getSourceId() + " could not be fetched in " + snap.getConsecutiveFailures()
                        + " consecutive checks (" + page.error() + "). Data may be out of date; verify manually. Nothing was changed.");
            }
            log.warn("Source {} fetch failed ({}); scheme data left untouched", snap.getSourceId(), page.error());
            return new CheckResult(snap.getSourceId(), snap.getUrl(), Outcome.FETCH_FAILED, page.status(), page.error());
        }

        snap.setConsecutiveFailures(0);
        snap.setLastSuccess(now);

        String discoveryNote = "";
        if ("SITEMAP".equals(snap.getKind()) || snap.isDiscoverLinks()) {
            SourceDiscoveryService.Result d = discovery.discover(snap, page);
            discoveryNote = "discovered " + d.found() + " scheme pages (" + d.registered() + " new). ";
            if ("SITEMAP".equals(snap.getKind())) {
                snap.setLastError(d.found() == 0 ? "No scheme pages matched the discovery filter." : null);
                snapshots.save(snap);
                return new CheckResult(snap.getSourceId(), snap.getUrl(), Outcome.DISCOVERED, page.status(), discoveryNote.trim());
            }
        }

        boolean structured = "B4S".equals(snap.getAdapter());
        boolean applyPath = structured || (snap.isAutoApply() && "SCHEME_PAGE".equals(snap.getKind()));
        if (applyPath) {
            List<SchemeCandidate> candidates = structured
                    ? BuddyBrandExtractor.extract(page.html(), snap.getUrl())
                    : HtmlPageExtractor.extract(page.html(), page.text(), snap.getUrl(), today);
            boolean nothingUsable = candidates.isEmpty() || (!structured && candidates.get(0).deadline() == null
                    && candidates.get(0).windowState() == null && candidates.get(0).evidence() == null);
            if (nothingUsable) {
                // A structured page without data is unreadable; a plain page that states no dates leaves records alone.
                snap.setLastError(structured ? "Page contains no structured programme data; nothing was changed." : null);
                snapshots.save(snap);
                if (structured) return new CheckResult(snap.getSourceId(), snap.getUrl(), Outcome.UNREADABLE, page.status(), snap.getLastError());
                return new CheckResult(snap.getSourceId(), snap.getUrl(), Outcome.UNCHANGED, page.status(), "The page states no dates; records left as they are.");
            }
            snap.setLastError(null);
            SchemeRefreshService.Outcome o = refresh.apply(snap, candidates, page);
            snapshots.save(snap);
            reindex(o.reindex());
            touchCovered(snap, now);
            boolean acted = o.updated() > 0 || o.created() > 0 || o.flagged() > 0;
            String detail = discoveryNote + o.updated() + " updated, " + o.created() + " created, " + o.flagged() + " flagged"
                    + (o.notes().isEmpty() ? "" : "; " + String.join("; ", o.notes().subList(0, Math.min(3, o.notes().size()))));
            return new CheckResult(snap.getSourceId(), snap.getUrl(), acted ? Outcome.UPDATED : Outcome.UNCHANGED, page.status(), detail);
        }

        // Plain pages: change detection on scheme-relevant text; changes flag for review and propose dates.
        String relevant = relevantText(page.text());
        if (relevant.isBlank()) {
            snap.setLastError("Page returned no readable scheme content (likely rendered by JavaScript); change detection unavailable.");
            snapshots.save(snap);
            return new CheckResult(snap.getSourceId(), snap.getUrl(), Outcome.UNREADABLE, page.status(), snap.getLastError());
        }
        snap.setLastError(null);

        List<ScholarshipEntity> covered = schemes.findBySourceId(snap.getSourceId()).stream()
                .filter(s -> "VERIFIED".equals(s.getVerificationStatus())).toList();
        for (ScholarshipEntity s : covered) {
            s.setLastChecked(now);
        }

        String hash = sha256(relevant);
        Outcome outcome;
        String detail;
        if (snap.getContentHash() == null) {
            outcome = Outcome.BASELINE;
            detail = "First successful fetch; baseline stored.";
        } else if (!snap.getContentHash().equals(hash)) {
            outcome = Outcome.CHANGED;
            snap.setLastChangeDetected(now);
            TreeSet<LocalDate> candidates = DeadlineExtractor.extract(page.text(), today);
            detail = "Source content changed" + (candidates.isEmpty() ? "." : "; deadline-like dates on page: " + candidates + ".");
            for (ScholarshipEntity s : covered) {
                s.setNeedsReview(true);
                s.setReviewReason("Official source " + snap.getSourceId() + " changed on " + today + ". Verify eligibility, benefits and dates at "
                        + snap.getUrl() + "." + (candidates.isEmpty() ? "" : " Dates seen near deadline wording: " + candidates + "."));
            }
            if (covered.size() == 1) {
                proposeDeadlines(covered.get(0), candidates, snap.getSourceId());
            }
        } else {
            outcome = Outcome.UNCHANGED;
            detail = "No change in scheme-relevant text.";
        }
        snap.setContentHash(hash);
        schemes.saveAll(covered);
        snapshots.save(snap);
        return new CheckResult(snap.getSourceId(), snap.getUrl(), outcome, page.status(), discoveryNote + detail);
    }

    /** Re-indexes after the transaction committed, so the vector store never sees data that was rolled back. */
    private void reindex(List<Long> ids) {
        for (Long id : ids) {
            schemes.findById(id).ifPresent(s -> {
                if ("VERIFIED".equals(s.getVerificationStatus())) indexer.indexSingleScholarship(s);
            });
        }
    }

    private void touchCovered(SourceSnapshotEntity snap, OffsetDateTime now) {
        List<ScholarshipEntity> covered = schemes.findBySourceId(snap.getSourceId());
        covered.forEach(s -> s.setLastChecked(now));
        schemes.saveAll(covered);
    }

    private void proposeDeadlines(ScholarshipEntity s, TreeSet<LocalDate> candidates, String sourceId) {
        for (LocalDate c : candidates) {
            if (c.equals(s.getDeadline())) continue;
            if (history.existsByScholarshipIdAndDeadlineAndState(s.getId(), c, DeadlineHistoryEntity.PROPOSED)) continue;
            history.save(new DeadlineHistoryEntity(s.getId(), s.getDeadline(), c, s.getAcademicYear(),
                    DeadlineHistoryEntity.PROPOSED, "SOURCE_CHECK", "Seen near deadline wording on " + sourceId + "; not verified"));
        }
    }

    private void flag(SourceSnapshotEntity snap, String reason) {
        List<ScholarshipEntity> covered = schemes.findBySourceId(snap.getSourceId());
        for (ScholarshipEntity s : covered) {
            s.setNeedsReview(true);
            s.setReviewReason(reason);
        }
        schemes.saveAll(covered);
    }

    static String relevantText(String text) {
        if (text == null || text.isBlank()) return "";
        StringBuilder sb = new StringBuilder();
        for (String sentence : text.split("(?<=[.!?])\\s+|\\s{3,}")) {
            if (RELEVANT.matcher(sentence).find()) {
                sb.append(sentence.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ")).append('\n');
            }
        }
        return sb.toString();
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
