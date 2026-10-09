package com.sam.wwol;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.Instant;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApplicationHealthControllerTest {
    private StatusService status;
    private PowerService power;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        status = mock(StatusService.class);
        power = mock(PowerService.class);
        when(power.wakeTransportAvailable()).thenReturn(true);
        mvc = MockMvcBuilders.standaloneSetup(new ApplicationHealthController(status, power)).build();
    }

    private void measurement(String target, Instant attempt, int failures, String reason) {
        when(status.getMeasurement()).thenReturn(new StatusService.Measurement(
                target, attempt, failures == 0 ? attempt : null, 10, failures, reason));
    }

    @Test
    void offlineTargetIsStillOperationalAndHealthNeverChangesPower() throws Exception {
        measurement("off", Instant.now(), 0, null);
        mvc.perform(get("/health/application"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.contract").value("application-health/v1"))
                .andExpect(jsonPath("$.status").value("operational"))
                .andExpect(jsonPath("$.coreAvailable").value(true))
                .andExpect(jsonPath("$.components.statusMonitor.status").value("operational"));
        verify(power, never()).turnOn();
        verify(power, never()).turnOff();
    }

    @Test
    void unmeasuredWorkerIsUnknown() throws Exception {
        mvc.perform(get("/health/application"))
                .andExpect(jsonPath("$.status").value("unknown"))
                .andExpect(jsonPath("$.coreAvailable").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.components.statusMonitor.reason").value("Not measured"));
    }

    @Test
    void stalledWorkerDoesNotAcquireAFreshSuccessTimestamp() throws Exception {
        Instant old = Instant.now().minusSeconds(40);
        measurement("on", old, 0, null);
        mvc.perform(get("/health/application"))
                .andExpect(jsonPath("$.status").value("unknown"))
                .andExpect(jsonPath("$.components.statusMonitor.reason").value("Monitoring result stale"))
                .andExpect(jsonPath("$.components.statusMonitor.lastSuccess").value(old.toString()));
    }

    @Test
    void pingExecutionFailureMakesCoreUnavailable() throws Exception {
        measurement("unknown", Instant.now(), 1, "Operation failed");
        mvc.perform(get("/health/application"))
                .andExpect(jsonPath("$.status").value("down"))
                .andExpect(jsonPath("$.coreAvailable").value(false));
    }

    @Test
    void invalidWakeConfigurationMakesCoreUnavailable() throws Exception {
        measurement("off", Instant.now(), 0, null);
        when(power.wakeTransportAvailable()).thenReturn(false);
        mvc.perform(get("/health/application"))
                .andExpect(jsonPath("$.status").value("down"))
                .andExpect(jsonPath("$.components.wakeTransport.status").value("down"));
    }
}
