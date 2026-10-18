package com.salon.controller;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.salon.service.HealthService;

@RestController
public class HealthController {
    private final HealthService service;
    public HealthController(HealthService service) {
        this.service=service;
    }
    @GetMapping("/health")
    public ResponseEntity<Map<String,String>> health() {
        boolean healthy=service.isHealthy();
        return ResponseEntity.status(healthy?200:503).body(Map.of("status",healthy?"UP":"DOWN"));
    }
}
