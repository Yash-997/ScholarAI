package com.scholarship.config;

import com.scholarship.service.SourceMonitorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs the official-source check on a cron schedule (default 03:30 IST daily). Disable with app.ingestion.enabled=false. */
@Component
@ConditionalOnProperty(name = "app.ingestion.enabled", havingValue = "true", matchIfMissing = true)
public class SourceCheckScheduler {

    private static final Logger log = LoggerFactory.getLogger(SourceCheckScheduler.class);

    private final SourceMonitorService monitor;

    public SourceCheckScheduler(SourceMonitorService monitor) {
        this.monitor = monitor;
    }

    @Scheduled(cron = "${app.ingestion.cron:0 30 3 * * *}", zone = "${app.timezone:Asia/Kolkata}")
    public void run() {
        log.info("Scheduled official-source check starting");
        try {
            monitor.checkAll();
        } catch (Exception e) {
            log.error("Scheduled source check failed; scheme data untouched: {}", e.getMessage(), e);
        }
    }
}
