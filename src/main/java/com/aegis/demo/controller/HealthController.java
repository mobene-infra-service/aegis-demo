package com.aegis.demo.controller;


import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/health")
public class HealthController {

    @GetMapping("/started")
    public ResponseEntity<Map<String, Object>> startupProbe() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "STARTED");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> readinessProbe() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "READY");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/live")
    public ResponseEntity<Map<String, Object>> livenessProbe() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ALIVE");
        return ResponseEntity.ok(response);
    }
}