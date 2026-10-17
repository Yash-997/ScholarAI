package com.scholarship.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Application clock. Scheme deadlines are Indian calendar dates, so "today" is evaluated in IST rather than the
 * container's default (UTC) zone; otherwise a scheme closing today would show as closed for 5.5 hours.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock applicationClock(@Value("${app.timezone:Asia/Kolkata}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
