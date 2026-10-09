package com.sam.wwol;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StatusServiceTest {
    private StatusService service;
    private Process process;

    @BeforeEach
    void setup() throws Exception {
        service = spy(new StatusService("127.0.0.1"));
        process = mock(Process.class);
        doReturn(process).when(service).startPing();
        when(process.waitFor(2, TimeUnit.SECONDS)).thenReturn(true);
    }

    @Test
    void offlineIsACompletedMeasurement() {
        when(process.exitValue()).thenReturn(1);
        service.updateStatus();
        assertEquals("off", service.getStatus());
        assertEquals(0, service.getMeasurement().consecutiveFailures());
        assertNotNull(service.getMeasurement().lastSuccess());
    }

    @Test
    void errorsPreserveLastSuccessAndRecover() {
        when(process.exitValue()).thenReturn(0, 2, 0);
        service.updateStatus();
        var success = service.getMeasurement().lastSuccess();
        service.updateStatus();
        assertEquals("unknown", service.getStatus());
        assertEquals(success, service.getMeasurement().lastSuccess());
        assertEquals(1, service.getMeasurement().consecutiveFailures());
        service.updateStatus();
        assertEquals("on", service.getStatus());
        assertEquals(0, service.getMeasurement().consecutiveFailures());
    }

    @Test
    void timedOutProcessIsTerminated() throws Exception {
        when(process.waitFor(2, TimeUnit.SECONDS)).thenReturn(false);
        when(process.isAlive()).thenReturn(true);
        service.updateStatus();
        assertEquals("Request timed out", service.getMeasurement().reason());
        verify(process).destroyForcibly();
    }

    @Test
    void missingPingIsFailureRatherThanOffline() throws Exception {
        doThrow(new IOException("private path")).when(service).startPing();
        service.updateStatus();
        assertEquals("unknown", service.getStatus());
        assertEquals("Operation failed", service.getMeasurement().reason());
        assertNull(service.getMeasurement().lastSuccess());
    }
}
