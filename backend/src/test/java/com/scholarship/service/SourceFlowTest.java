package com.scholarship.service;

import com.scholarship.entity.ScholarshipEntity;
import com.scholarship.entity.SourceSnapshotEntity;
import com.scholarship.repository.DeadlineHistoryRepository;
import com.scholarship.repository.ScholarshipRepository;
import com.scholarship.repository.SourceSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Discovery and the whole monitor flow (fetch, extract, refresh, re-index) with a fake website. */
@DataJpaTest
class SourceFlowTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-17T06:00:00Z"), ZoneId.of("Asia/Kolkata"));
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

    @Autowired SourceSnapshotRepository snapshots;
    @Autowired ScholarshipRepository schemes;
    @Autowired DeadlineHistoryRepository history;
    @Autowired PlatformTransactionManager tx;

    private final Map<String, PageFetcher.Page> site = new ConcurrentHashMap<>();
    private final PageFetcher fetcher = url -> site.getOrDefault(url, PageFetcher.Page.failure(404, "HTTP 404"));
    private ScholarshipIngestionService indexer;
    private SourceRegistry registry;
    private SourceMonitorService monitor;

    @BeforeEach
    void setUp() {
        indexer = mock(ScholarshipIngestionService.class);
        registry = mock(SourceRegistry.class);
        monitor = monitor(fetcher);
    }

    private SourceMonitorService monitor(PageFetcher f) {
        return new SourceMonitorService(snapshots, schemes, history, registry, f, CLOCK,
                new SchemeRefreshService(schemes, history, tx, CLOCK), new SourceDiscoveryService(snapshots, f, 60), indexer);
    }

    private static PageFetcher.Page xml(String body) {
        return new PageFetcher.Page(true, 200, "", null, body);
    }

    // ---- discovery ------------------------------------------------------------------------------

    private SourceSnapshotEntity sitemapSource() {
        SourceSnapshotEntity s = new SourceSnapshotEntity("SRC-D1", "https://www.buddy4study.com/sitemap.xml", "Buddy4Study", "Sitemap");
        s.setKind("SITEMAP");
        s.setAdapter("B4S");
        s.setTrust("PARTNER");
        s.setItemMatch("sitemap(1|11)\\.xml$");
        s.setLinkFilter("^(?!.*loan).*(tata|kotak|hdfc).*$");
        return s;
    }

    @Test
    void sitemapDiscoveryRegistersOnlyMatchingBrandPages_fromTheSelectedChildSitemaps() {
        site.put("https://www.buddy4study.com/sitemap.xml", xml("<sitemapindex><sitemap><loc>https://www.buddy4study.com/sitemap1.xml</loc></sitemap>"
                + "<sitemap><loc>https://www.buddy4study.com/sitemap5.xml</loc></sitemap><sitemap><loc>https://www.buddy4study.com/sitemap11.xml</loc></sitemap></sitemapindex>"));
        site.put("https://www.buddy4study.com/sitemap1.xml", xml("<urlset><url><loc>https://www.buddy4study.com/page/the-tata-capital-pankh</loc></url></urlset>"));
        site.put("https://www.buddy4study.com/sitemap11.xml", xml("<urlset><url><loc>https://www.buddy4study.com/page/kotak-kanya-scholarship</loc></url>"
                + "<url><loc>https://www.buddy4study.com/page/hdfc-education-loan</loc></url><url><loc>https://www.buddy4study.com/page/random-ngo</loc></url>"
                + "<url><loc>https://evil.example/page/tata-fake</loc></url></urlset>"));
        site.put("https://www.buddy4study.com/sitemap5.xml", xml("<urlset><url><loc>https://www.buddy4study.com/page/tata-must-not-be-read</loc></url></urlset>"));

        SourceDiscoveryService.Result r = new SourceDiscoveryService(snapshots, fetcher, 60).discover(sitemapSource(), site.get("https://www.buddy4study.com/sitemap.xml"));

        assertEquals(2, r.found(), "loan pages, other providers, other hosts and unselected child sitemaps are excluded");
        assertEquals(2, r.registered());
        List<SourceSnapshotEntity> found = snapshots.findAll();
        assertEquals(2, found.size());
        assertTrue(found.stream().allMatch(SourceSnapshotEntity::isDiscovered));
        assertTrue(found.stream().allMatch(s -> "SRC-D1".equals(s.getDiscoveredFrom())));
    }

    @Test
    void discoveryIsIdempotent_andNeverReRegistersACuratedUrl() {
        SourceSnapshotEntity curated = new SourceSnapshotEntity("SRC-15", "https://www.buddy4study.com/page/kotak-kanya-scholarship/", "Kotak", "x");
        snapshots.save(curated);
        site.put("https://www.buddy4study.com/sitemap.xml", xml("<urlset><url><loc>https://www.buddy4study.com/page/kotak-kanya-scholarship</loc></url>"
                + "<url><loc>https://www.buddy4study.com/page/tata-new</loc></url></urlset>"));
        SourceDiscoveryService d = new SourceDiscoveryService(snapshots, fetcher, 60);
        assertEquals(1, d.discover(sitemapSource(), site.get("https://www.buddy4study.com/sitemap.xml")).registered());
        assertEquals(0, d.discover(sitemapSource(), site.get("https://www.buddy4study.com/sitemap.xml")).registered());
        assertEquals(2, snapshots.count());
    }

    @Test
    void discoveryRespectsTheCap() {
        StringBuilder sb = new StringBuilder("<urlset>");
        for (int i = 0; i < 30; i++) sb.append("<url><loc>https://www.buddy4study.com/page/tata-").append(i).append("</loc></url>");
        PageFetcher.Page p = xml(sb.append("</urlset>").toString());
        assertEquals(5, new SourceDiscoveryService(snapshots, fetcher, 5).discover(sitemapSource(), p).registered());
    }

    @Test
    void linkDiscoveryKeepsSameHostSchemeLinksOnly() {
        SourceSnapshotEntity portal = new SourceSnapshotEntity("SRC-04", "https://mahadbt.maharashtra.gov.in/", "Maharashtra", "MahaDBT");
        portal.setKind("PORTAL");
        portal.setLinkFilter("SchemeData");
        portal.setDiscoverLinks(true);
        String html = "<html><body><a href=\"/SchemeData/SchemeData?str=AAA\">Eklavya Scholarship</a>"
                + "<a href=\"/SchemeData/SchemeData?str=BBB\">Post Matric</a><a href=\"/SchemeData/SchemeData?str=AAA\">dup</a>"
                + "<a href=\"/SchemeData/guide.pdf\">PDF SchemeData</a><a href=\"https://other.example/SchemeData?x=1\">off-site</a>"
                + "<a href=\"/Home/About\">About</a></body></html>";
        SourceDiscoveryService.Result r = new SourceDiscoveryService(snapshots, fetcher, 60)
                .discover(portal, new PageFetcher.Page(true, 200, "", null, html));
        assertEquals(2, r.registered());
        assertTrue(snapshots.findAll().stream().allMatch(s -> "SCHEME_PAGE".equals(s.getKind()) && "HTML".equals(s.getAdapter())
                && !s.isAutoApply() && s.getUrl().startsWith("https://mahadbt.maharashtra.gov.in/SchemeData/SchemeData?str=")));
    }

    // ---- the monitor flow -----------------------------------------------------------------------

    private ScholarshipEntity curatedScheme() {
        ScholarshipEntity s = TestSchemes.scheme(0, "Kotak Kanya Scholarship");
        s.setId(null);
        s.setSourceId("SRC-15");
        return schemes.save(s);
    }

    private void registerKotak() {
        when(registry.load()).thenReturn(List.of(new SourceRegistry.Entry("SRC-15", "Kotak", "B4S page", "https://www.buddy4study.com/page/kotak-kanya-scholarship",
                null, List.of("21"), "", "BRAND_PAGE", "B4S", "PARTNER", "Kotak Kanya", true, false, null)));
    }

    private static PageFetcher.Page kotakPage(String deadline) {
        return TestPages.ok(TestPages.brandPage(
                TestPages.item(26763, "Kotak Kanya Scholarship 2026-27", deadline, "2026-07-14", "2026", "Girls with 75%."),
                TestPages.item(25304, "Kotak Kanya Scholarship 2025-26", "2026-01-25", "2025-06-13", "2025", "Old.")));
    }

    @Test
    void aPublishedDeadlineFlowsFromThePageToTheRecordAndTheIndex() {
        registerKotak();
        ScholarshipEntity s = curatedScheme();
        site.put("https://www.buddy4study.com/page/kotak-kanya-scholarship", kotakPage("2026-11-15"));

        List<SourceMonitorService.CheckResult> results = monitor.checkAll();

        assertEquals(SourceMonitorService.Outcome.UPDATED, results.get(0).outcome(), results.get(0).detail());
        ScholarshipEntity after = schemes.findById(s.getId()).orElseThrow();
        assertEquals(LocalDate.of(2026, 11, 15), after.getDeadline());
        assertEquals(SchemeStatus.OPEN, SchemeStatusCalculator.compute(after, TODAY).status());
        verify(indexer, atLeastOnce()).indexSingleScholarship(argThat(x -> x.getId().equals(s.getId())));
    }

    @Test
    void aFailedFetchPreservesTheLastValidDeadline_andDoesNotReindex() {
        registerKotak();
        ScholarshipEntity s = curatedScheme();
        site.put("https://www.buddy4study.com/page/kotak-kanya-scholarship", kotakPage("2026-11-15"));
        monitor.checkAll();
        reset(indexer);

        site.put("https://www.buddy4study.com/page/kotak-kanya-scholarship", PageFetcher.Page.failure(503, "HTTP 503"));
        SourceMonitorService.CheckResult r = monitor.checkAll().get(0);

        assertEquals(SourceMonitorService.Outcome.FETCH_FAILED, r.outcome());
        ScholarshipEntity after = schemes.findById(s.getId()).orElseThrow();
        assertEquals(LocalDate.of(2026, 11, 15), after.getDeadline());
        assertEquals(SchemeStatus.OPEN, SchemeStatusCalculator.compute(after, TODAY).status(), "a failed fetch is not evidence of closure");
        verifyNoInteractions(indexer);
    }

    @Test
    void aPageWithNoProgrammeDataIsUnreadable_andLeavesTheRecordAlone() {
        registerKotak();
        ScholarshipEntity s = curatedScheme();
        site.put("https://www.buddy4study.com/page/kotak-kanya-scholarship", kotakPage("2026-11-15"));
        monitor.checkAll();
        site.put("https://www.buddy4study.com/page/kotak-kanya-scholarship", TestPages.ok(TestPages.emptyShell()));
        assertEquals(SourceMonitorService.Outcome.UNREADABLE, monitor.checkAll().get(0).outcome());
        assertEquals(LocalDate.of(2026, 11, 15), schemes.findById(s.getId()).orElseThrow().getDeadline());
    }

    @Test
    void rerunningWithAnUnchangedPageDoesNotReindexOrWriteHistoryAgain() {
        registerKotak();
        ScholarshipEntity s = curatedScheme();
        site.put("https://www.buddy4study.com/page/kotak-kanya-scholarship", kotakPage("2026-11-15"));
        monitor.checkAll();
        reset(indexer);
        monitor.checkAll();
        monitor.checkAll();
        verifyNoInteractions(indexer);
        assertEquals(1, history.findByScholarshipIdOrderByRecordedAtDesc(s.getId()).size());
    }

    @Test
    void anExtendedDeadlineIsPickedUpOnTheNextRun() {
        registerKotak();
        ScholarshipEntity s = curatedScheme();
        site.put("https://www.buddy4study.com/page/kotak-kanya-scholarship", kotakPage("2026-11-15"));
        monitor.checkAll();
        site.put("https://www.buddy4study.com/page/kotak-kanya-scholarship", kotakPage("2026-12-10"));
        monitor.checkAll();
        assertEquals(LocalDate.of(2026, 12, 10), schemes.findById(s.getId()).orElseThrow().getDeadline());
        assertEquals(2, history.findByScholarshipIdOrderByRecordedAtDesc(s.getId()).size());
    }

    @Test
    void discoveryRunsFirst_soNewPagesAreCheckedInTheSameRun() {
        SourceRegistry.Entry sitemap = new SourceRegistry.Entry("SRC-D1", "Buddy4Study", "Sitemap", "https://www.buddy4study.com/sitemap.xml", null, List.of(), "",
                "SITEMAP", "B4S", "PARTNER", null, false, true, "kotak");
        when(registry.load()).thenReturn(List.of(sitemap));
        site.put("https://www.buddy4study.com/sitemap.xml", xml("<urlset><url><loc>https://www.buddy4study.com/page/kotak-junior</loc></url></urlset>"));
        site.put("https://www.buddy4study.com/page/kotak-junior", TestPages.ok(TestPages.brandPage(
                TestPages.item(900, "Kotak Junior Scholarship 2026-27", "2026-11-30", "2026-08-01", "2026", "Students need at least 75% marks."))));

        List<SourceMonitorService.CheckResult> results = monitor.checkAll();

        assertEquals(2, results.size());
        assertEquals(SourceMonitorService.Outcome.DISCOVERED, results.get(0).outcome());
        ScholarshipEntity staged = schemes.findByExternalKey("b4s:900").get(0);
        assertEquals("PENDING_REVIEW", staged.getVerificationStatus());
        assertEquals(LocalDate.of(2026, 11, 30), staged.getDeadline());
        verifyNoInteractions(indexer);   // staged records are not searchable evidence
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void overlappingRunsAreSkipped_soTheJobIsSafeToTriggerTwice() throws Exception {
        registerKotak();
        curatedScheme();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        PageFetcher blocking = url -> {
            entered.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            return kotakPage("2026-11-15");
        };
        SourceMonitorService m = monitor(blocking);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<List<SourceMonitorService.CheckResult>> first = pool.submit(m::checkAll);
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            assertTrue(m.isRunning());
            List<SourceMonitorService.CheckResult> second = m.checkAll();
            assertEquals(SourceMonitorService.Outcome.SKIPPED, second.get(0).outcome());
            release.countDown();
            assertEquals(SourceMonitorService.Outcome.UPDATED, first.get(15, TimeUnit.SECONDS).get(0).outcome());
            assertFalse(m.isRunning());
        } finally {
            release.countDown();
            pool.shutdownNow();
            history.deleteAll();     // this test commits (another thread must see the data), so it cleans up after itself
            schemes.deleteAll();
            snapshots.deleteAll();
        }
    }
}
