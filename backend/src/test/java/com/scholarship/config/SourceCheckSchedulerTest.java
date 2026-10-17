package com.scholarship.config;

import com.scholarship.service.SourceMonitorService;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SourceCheckSchedulerTest {

    @Test
    void staleInstanceRunsACheckAfterStartup() {
        SourceMonitorService monitor = mock(SourceMonitorService.class);
        when(monitor.isStale(Duration.ofHours(24))).thenReturn(true);
        new SourceCheckScheduler(monitor, 24).catchUp();
        verify(monitor).checkAll();
    }

    @Test
    void recentlyCheckedInstanceIsLeftAlone() {
        SourceMonitorService monitor = mock(SourceMonitorService.class);
        when(monitor.isStale(any())).thenReturn(false);
        new SourceCheckScheduler(monitor, 24).catchUp();
        verify(monitor, never()).checkAll();
    }

    @Test
    void failureNeverEscapes() {
        SourceMonitorService monitor = mock(SourceMonitorService.class);
        when(monitor.isStale(any())).thenThrow(new IllegalStateException("db down"));
        new SourceCheckScheduler(monitor, 24).catchUp();
    }
}
