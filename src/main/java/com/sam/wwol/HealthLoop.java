package com.sam.wwol;

import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

@Component
public class HealthLoop {

    private final StatusService statusService;

    public HealthLoop(StatusService statusService) {
        this.statusService = statusService;
    }

    @PostConstruct
    public void start() {
        Thread t = new Thread(() -> {
            while (true) {
                statusService.updateStatus();
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException ignored) {}
            }
        });

        t.setDaemon(true);
        t.start();
    }
}
