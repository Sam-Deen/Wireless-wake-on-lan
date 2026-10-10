package com.sam.wwol;

import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

@Component
public class HealthLoop {

    private final StatusService statusService;

    public HealthLoop(StatusService statusService) {
        this.statusService = statusService;
    }

    @Scheduled(fixedDelayString = "#{@healthSettings.statusInterval()}")
    public void update() {
        statusService.updateStatus();
    }
}
