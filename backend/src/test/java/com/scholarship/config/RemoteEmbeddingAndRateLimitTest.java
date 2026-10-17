package com.scholarship.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scholarship.service.RetrievalInfo;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class RemoteEmbeddingAndRateLimitTest {

    private HttpServer server;
    private String base;
    private final AtomicInteger calls = new AtomicInteger();
    private final List<String> lastAuth = new ArrayList<>();
    private final List<Integer> batchSizes = new ArrayList<>();
    private volatile int failFirst = 0;
    private volatile int dims = 4;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ObjectMapper om = new ObjectMapper();
        server.createContext("/v1/embeddings", ex -> {
            int n = calls.incrementAndGet();
            lastAuth.add(ex.getRequestHeaders().getFirst("Authorization"));
            if (n <= failFirst) {
                ex.sendResponseHeaders(503, -1);
                ex.close();
                return;
            }
            JsonNode req = om.readTree(ex.getRequestBody());
            int size = req.path("input").size();
            synchronized (batchSizes) { batchSizes.add(size); }
            StringBuilder sb = new StringBuilder("{\"data\":[");
            for (int i = 0; i < size; i++) {
                if (i > 0) sb.append(',');
                sb.append("{\"embedding\":[");
                for (int d = 0; d < dims; d++) sb.append(d > 0 ? "," : "").append(0.1 * (i + 1));
                sb.append("]}");
            }
            byte[] b = sb.append("]}").toString().getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void remoteModelCallsTheEndpointWithTheKeyFromConfiguration() {
        RemoteEmbeddingModel m = new RemoteEmbeddingModel(base, "secret-key", "test-model", 4);
        float[] v = m.embed("scholarship for girls");
        assertEquals(4, v.length);
        assertEquals("Bearer secret-key", lastAuth.get(0));
        assertEquals(4, m.dimensions());
    }

    @Test
    void textsAreSentInBatches() {
        RemoteEmbeddingModel m = new RemoteEmbeddingModel(base, "k", "test-model", 4);
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < 70; i++) texts.add("t" + i);
        assertEquals(70, m.embed(texts).size());
        assertEquals(List.of(32, 32, 6), batchSizes);
    }

    @Test
    void transientFailuresAreRetried() {
        failFirst = 2;
        RemoteEmbeddingModel m = new RemoteEmbeddingModel(base, "k", "test-model", 4);
        assertEquals(4, m.embed("x").length);
        assertEquals(3, calls.get());
    }

    @Test
    void aDimensionMismatchIsAnErrorNotSilentlyTruncated() {
        RemoteEmbeddingModel m = new RemoteEmbeddingModel(base, "k", "test-model", 8);   // server returns 4
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> m.embed("x"));
        assertTrue(e.getMessage().contains("dimensions"));
    }

    @Test
    void persistentFailureSurfaces() {
        failFirst = 100;
        RemoteEmbeddingModel m = new RemoteEmbeddingModel(base, "k", "test-model", 4);
        assertThrows(IllegalStateException.class, () -> m.embed("x"));
    }

    @Test
    void misconfigurationFailsAtStartup() {
        assertThrows(IllegalStateException.class, () -> new RemoteEmbeddingModel("", "k", "m", 4));
        assertThrows(IllegalStateException.class, () -> new RemoteEmbeddingModel(base, "k", "", 4));
    }

    @Test
    void retrievalIsOnlyDescribedAsSemanticWhenARealModelIsConfigured() {
        assertEquals("keyword", new RetrievalInfo("lexical", "").mode());
        assertEquals("keyword", new RetrievalInfo().mode());
        assertEquals("semantic", new RetrievalInfo("openai-compatible", "").mode());
        assertEquals(0.2, new RetrievalInfo("lexical", "").minSimilarity());
        assertEquals(0.35, new RetrievalInfo("openai-compatible", "").minSimilarity());
        assertEquals(0.5, new RetrievalInfo("lexical", "0.5").minSimilarity());
        assertEquals(0.2, new RetrievalInfo("lexical", "junk").minSimilarity());
    }

    // ---- rate limiting -------------------------------------------------------------------------

    private static class MutableClock extends Clock {
        long now = 1_000_000;
        public ZoneId getZone() { return ZoneId.of("UTC"); }
        public Clock withZone(ZoneId z) { return this; }
        public Instant instant() { return Instant.ofEpochMilli(now); }
        public long millis() { return now; }
    }

    private int post(RateLimitFilter f, String path, String ip, String realIp) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", path);
        req.setRemoteAddr(ip);
        if (realIp != null) req.addHeader("X-Real-IP", realIp);
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(req, res, mock(FilterChain.class));
        return res.getStatus();
    }

    @Test
    void chatIsLimitedPerClientPerMinute_andRecoversAfterTheWindow() throws Exception {
        MutableClock clock = new MutableClock();
        RateLimitFilter f = new RateLimitFilter(3, 5, false, clock);
        for (int i = 0; i < 3; i++) assertEquals(200, post(f, "/api/v1/scholarships/chat", "10.0.0.1", null));
        assertEquals(429, post(f, "/api/v1/scholarships/chat", "10.0.0.1", null));
        assertEquals(200, post(f, "/api/v1/scholarships/chat", "10.0.0.2", null), "another client is unaffected");
        clock.now += 61_000;
        assertEquals(200, post(f, "/api/v1/scholarships/chat", "10.0.0.1", null));
    }

    @Test
    void tooManyResponseCarriesRetryAfter() throws Exception {
        RateLimitFilter f = new RateLimitFilter(1, 1, false, new MutableClock());
        post(f, "/api/v1/scholarships/recommend", "10.0.0.1", null);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/scholarships/recommend");
        req.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(req, res, mock(FilterChain.class));
        assertEquals(429, res.getStatus());
        assertNotNull(res.getHeader("Retry-After"));
    }

    @Test
    void otherEndpointsAndReadsAreNotLimited() throws Exception {
        RateLimitFilter f = new RateLimitFilter(1, 1, false, new MutableClock());
        for (int i = 0; i < 5; i++) {
            assertEquals(200, post(f, "/api/v1/scholarships", "10.0.0.1", null));
            MockHttpServletRequest get = new MockHttpServletRequest("GET", "/api/v1/scholarships/chat");
            MockHttpServletResponse res = new MockHttpServletResponse();
            f.doFilter(get, res, mock(FilterChain.class));
            assertEquals(200, res.getStatus());
        }
    }

    @Test
    void behindTheProxy_clientsAreToldApartByTheRealIpHeader_onlyWhenTrusted() throws Exception {
        RateLimitFilter trusting = new RateLimitFilter(1, 1, true, new MutableClock());
        assertEquals(200, post(trusting, "/api/v1/scholarships/chat", "172.18.0.5", "203.0.113.1"));
        assertEquals(200, post(trusting, "/api/v1/scholarships/chat", "172.18.0.5", "203.0.113.2"));
        assertEquals(429, post(trusting, "/api/v1/scholarships/chat", "172.18.0.5", "203.0.113.1"));
        RateLimitFilter strict = new RateLimitFilter(1, 1, false, new MutableClock());
        assertEquals(200, post(strict, "/api/v1/scholarships/chat", "172.18.0.5", "203.0.113.1"));
        assertEquals(429, post(strict, "/api/v1/scholarships/chat", "172.18.0.5", "203.0.113.2"), "a spoofable header is ignored unless trusted");
    }
}
