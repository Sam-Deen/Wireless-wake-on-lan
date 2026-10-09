package com.sam.wwol;

import org.springframework.web.bind.annotation.*;

@RestController
public class PowerController {

    private final StatusService statusService;
    private final PowerService powerService;

    public PowerController(StatusService statusService, PowerService powerService) {
        this.statusService = statusService;
        this.powerService = powerService;
    }

    @GetMapping("/status")
    public String getStatus() {
        return statusService.getStatus();
    }

    @PostMapping("/power/on")
    public String powerOn() {
        return powerService.turnOn();
    }

    @PostMapping("/power/off")
    public String powerOff() {
        return powerService.turnOff();
    }
}
