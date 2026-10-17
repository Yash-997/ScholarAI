package com.scholarship.config;

import com.scholarship.service.SourceMonitorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the official-source check on a cron schedule (default 03:30 IST daily) and once after startup when the last
 * successful check is older than app.ingestion.startup-max-age-hours, so a new or sleeping instance converges to the same
 * dates as every other instance. Disable with app.ingestion.enabled=false.
 */
@Component
@ConditionalOnProperty(name = "app.ingestion.enabled", havingValue = "true", matchIfMissing = true)
public class SourceCheckScheduler {

    private static final Logger log = LoggerFactory.getLogger(SourceCheckScheduler.class);

    private final SourceMonitorService monitor;
    private final long startupMaxAgeHours;

    public SourceCheckScheduler(SourceMonitorService monitor,
                                @Value("${app.ingestion.startup-max-age-hours:24}") long startupMaxAgeHours) {
        this.monitor = monitor;
        this.startupMaxAgeHours = startupMaxAgeHours;
    }

    /** Fires after the dataset loader has run. Background thread: fetching must not delay the port binding or health checks. */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (startupMaxAgeHours <= 0) return;
        Thread t = new Thread(this::catchUp, "startup-source-check");
        t.setDaemon(true);
        t.start();
    }

    void catchUp() {
        try {
            if (monitor.isStale(java.time.Duration.ofHours(startupMaxAgeHours))) {
                log.info("No successful source check in the last {}h; running one now", startupMaxAgeHours);
                run();
            }
        } catch (Exception e) {
            log.error("Startup source check failed; scheme data untouched: {}", e.getMessage(), e);
        }
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
