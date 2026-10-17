package com.scholarship.controller;

import com.scholarship.config.WebCorsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class HealthAndCorsTest {

    private MockMvc mvc(String origins) {
        return MockMvcBuilders.standaloneSetup(new HealthController())
                .addFilters(new WebCorsConfig().corsFilter(origins).getFilter())
                .build();
    }

    @Test
    void healthReturnsUpWithoutAuth() throws Exception {
        mvc("https://app.vercel.app").perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\",\"service\":\"SCHOLAR.AI Backend\"}", true));
    }

    @Test
    void configuredOriginIsAllowedAndOthersAreNot() throws Exception {
        MockMvc mvc = mvc("https://app.vercel.app/, http://localhost:5173");
        mvc.perform(get("/api/health").header("Origin", "https://app.vercel.app"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://app.vercel.app"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        mvc.perform(get("/api/health").header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden());
    }

    @Test
    void preflightIsAnsweredForChatPost() throws Exception {
        mvc("https://app.vercel.app").perform(options("/api/v1/scholarships/chat")
                        .header("Origin", "https://app.vercel.app")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://app.vercel.app"));
    }
}
