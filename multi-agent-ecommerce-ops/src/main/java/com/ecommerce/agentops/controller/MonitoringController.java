package com.ecommerce.agentops.controller;

import com.ecommerce.agentops.agent.monitoring.MonitoringAgent;
import com.ecommerce.agentops.model.enums.AlertLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 监控API
 */
@RestController
@RequestMapping("/api/monitoring")
@RequiredArgsConstructor
public class MonitoringController {

    private final MonitoringAgent monitoringAgent;

    /**
     * 获取系统概览
     */
    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> getOverview() {
        return ResponseEntity.ok(monitoringAgent.getSystemOverview());
    }

    /**
     * 获取最近告警
     */
    @GetMapping("/alerts")
    public ResponseEntity<List<MonitoringAgent.Alert>> getAlerts(
            @RequestParam(defaultValue = "50") int count) {
        return ResponseEntity.ok(monitoringAgent.getRecentAlerts(count));
    }

    /**
     * 按级别获取告警
     */
    @GetMapping("/alerts/level/{level}")
    public ResponseEntity<List<MonitoringAgent.Alert>> getAlertsByLevel(
            @PathVariable AlertLevel level) {
        return ResponseEntity.ok(monitoringAgent.getAlertsByLevel(level));
    }
}
