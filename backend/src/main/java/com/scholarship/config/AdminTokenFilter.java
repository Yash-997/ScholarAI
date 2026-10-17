package com.scholarship.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

/**
 * Protects write and admin endpoints with a shared secret in the X-Admin-Token header.
 *
 * Protected: everything under /api/v1/admin/ and every non-read request under /api/v1/scholarships except the two
 * public POSTs (/recommend and /chat). When ADMIN_API_TOKEN is not set the guard is off so local development keeps
 * working, and a warning is logged at startup. Set it on any server reachable by other people.
 */
@Component
public class AdminTokenFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AdminTokenFilter.class);
    private static final Set<String> PUBLIC_POSTS = Set.of("/api/v1/scholarships/recommend", "/api/v1/scholarships/chat");
    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final byte[] token;

    public AdminTokenFilter(@Value("${app.security.admin-token:}") String token) {
        this.token = token == null || token.isBlank() ? null : token.getBytes(StandardCharsets.UTF_8);
        if (this.token == null) {
            log.warn("ADMIN_API_TOKEN is not set: admin and write endpoints are open. Set it before exposing this server.");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (token != null && isProtected(request) && !matches(request.getHeader("X-Admin-Token"))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"A valid X-Admin-Token header is required.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    static boolean isProtected(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/api/v1/admin/")) return true;
        if (!path.startsWith("/api/v1/scholarships")) return false;
        if (READ_METHODS.contains(request.getMethod())) return false;
        return !PUBLIC_POSTS.contains(path);
    }

    private boolean matches(String supplied) {
        return supplied != null && MessageDigest.isEqual(token, supplied.getBytes(StandardCharsets.UTF_8));
    }
}
