package com.scholarship.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class AdminTokenFilterTest {

    private int run(String token, String method, String path, String header) throws Exception {
        AdminTokenFilter filter = new AdminTokenFilter(token);
        MockHttpServletRequest req = new MockHttpServletRequest(method, path);
        if (header != null) req.addHeader("X-Admin-Token", header);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(req, res, chain);
        return res.getStatus();
    }

    @Test
    void withATokenConfigured_adminAndWriteEndpointsNeedIt() throws Exception {
        assertEquals(401, run("s3cret", "POST", "/api/v1/admin/sources/check", null));
        assertEquals(401, run("s3cret", "POST", "/api/v1/admin/sources/check", "wrong"));
        assertEquals(200, run("s3cret", "POST", "/api/v1/admin/sources/check", "s3cret"));
        assertEquals(401, run("s3cret", "DELETE", "/api/v1/scholarships/5", null));
        assertEquals(401, run("s3cret", "POST", "/api/v1/scholarships/ingest", null));
        assertEquals(401, run("s3cret", "PUT", "/api/v1/scholarships/5", null));
    }

    @Test
    void publicEndpointsStayOpen() throws Exception {
        assertEquals(200, run("s3cret", "POST", "/api/v1/scholarships/recommend", null));
        assertEquals(200, run("s3cret", "POST", "/api/v1/scholarships/chat", null));
        assertEquals(200, run("s3cret", "GET", "/api/v1/scholarships", null));
        assertEquals(200, run("s3cret", "GET", "/api/v1/scholarships/5", null));
    }

    @Test
    void withoutATokenConfigured_nothingIsBlocked() throws Exception {
        assertEquals(200, run("", "POST", "/api/v1/admin/sources/check", null));
        assertEquals(200, run(null, "DELETE", "/api/v1/scholarships/5", null));
    }
}
