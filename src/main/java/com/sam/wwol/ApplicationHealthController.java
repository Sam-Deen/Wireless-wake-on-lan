package com.sam.wwol;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class ApplicationHealthController {
    private final ApplicationHealthMonitor monitor;
    public ApplicationHealthController(ApplicationHealthMonitor monitor) { this.monitor = monitor; }
    @GetMapping(value = "/health/application", produces = "application/json")
    public Map<String, Object> health() { return monitor.snapshot(); }
}
