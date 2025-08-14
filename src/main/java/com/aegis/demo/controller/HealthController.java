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



    // =============== Kubernetes 探针端点 ===============

    /**
     * Startup Probe - 检查应用是否已完成启动
     * Kubernetes 使用此端点来确定应用是否已启动完成
     *
     * @return HTTP 200 (应用已启动) 或 HTTP 503 (应用仍在启动中)
     */
    @GetMapping("/started")
    public ResponseEntity<Map<String, Object>> startupProbe() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "STARTED");

        return ResponseEntity.ok(response);
    }

    /**
     * Readiness Probe - 检查应用是否准备好接收流量
     * Kubernetes 使用此端点来确定是否将流量路由到此实例
     *
     * @return HTTP 200 (应用已准备好) 或 HTTP 503 (应用未准备好)
     */
    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> readinessProbe() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "READY");

        return ResponseEntity.ok(response);
    }

    /**
     * Liveness Probe - 检查应用是否仍然存活
     * Kubernetes 使用此端点来确定是否需要重启容器
     *
     * @return HTTP 200 (应用存活) 或 HTTP 503 (应用已死亡)
     */
    @GetMapping("/live")
    public ResponseEntity<Map<String, Object>> livenessProbe() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ALIVE");

        return ResponseEntity.ok(response);
    }

}