package com.scholarship.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-client limits on the two public endpoints that cost something (the chatbot calls an external model).
 * Fixed one-minute windows keyed by client address. Behind the bundled Nginx every request arrives from the proxy, so
 * set RATE_LIMIT_TRUST_REAL_IP=true there: Nginx overwrites X-Real-IP with the real client address.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String CHAT = "/api/v1/scholarships/chat";
    private static final String RECOMMEND = "/api/v1/scholarships/recommend";

    private record Window(long startMs, int count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final int chatPerMinute;
    private final int recommendPerMinute;
    private final boolean trustRealIp;
    private final Clock clock;

    public RateLimitFilter(@Value("${app.rate-limit.chat-per-minute:20}") int chatPerMinute,
                           @Value("${app.rate-limit.recommend-per-minute:60}") int recommendPerMinute,
                           @Value("${app.rate-limit.trust-real-ip-header:false}") boolean trustRealIp,
                           Clock clock) {
        this.chatPerMinute = chatPerMinute;
        this.recommendPerMinute = recommendPerMinute;
        this.trustRealIp = trustRealIp;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        int limit = "POST".equals(request.getMethod()) ? (path.equals(CHAT) ? chatPerMinute : path.equals(RECOMMEND) ? recommendPerMinute : 0) : 0;
        if (limit > 0) {
            long now = clock.millis();
            String key = path + "|" + clientAddress(request);
            Window w = windows.merge(key, new Window(now, 1), (old, fresh) -> now - old.startMs >= 60_000 ? fresh : new Window(old.startMs, old.count + 1));
            if (w.count > limit) {
                long retry = Math.max(1, (60_000 - (now - w.startMs) + 999) / 1000);
                response.setStatus(429);
                response.setHeader("Retry-After", String.valueOf(retry));
                response.setContentType("application/json");
                response.getWriter().write("{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Too many requests. Try again in " + retry + " seconds.\"}");
                return;
            }
            if (windows.size() > 10_000) windows.entrySet().removeIf(e -> now - e.getValue().startMs >= 60_000);
        }
        chain.doFilter(request, response);
    }

    private String clientAddress(HttpServletRequest request) {
        if (trustRealIp) {
            String real = request.getHeader("X-Real-IP");
            if (real != null && !real.isBlank()) return real.trim();
        }
        return request.getRemoteAddr();
    }
}
