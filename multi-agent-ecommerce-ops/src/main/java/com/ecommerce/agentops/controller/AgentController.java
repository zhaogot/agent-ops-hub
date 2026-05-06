package com.ecommerce.agentops.controller;

import com.ecommerce.agentops.agent.core.Agent;
import com.ecommerce.agentops.agent.core.AgentRegistry;
import com.ecommerce.agentops.model.dto.AgentStatusDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Agent管理API
 */
@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentRegistry agentRegistry;

    /**
     * 获取所有Agent状态
     */
    @GetMapping("/status")
    public ResponseEntity<List<AgentStatusDTO>> getAllAgentStatus() {
        List<AgentStatusDTO> statuses = agentRegistry.getAllAgents().stream()
                .map(this::toStatusDTO)
                .toList();
        return ResponseEntity.ok(statuses);
    }

    /**
     * 获取指定Agent状态
     */
    @GetMapping("/{agentId}/status")
    public ResponseEntity<AgentStatusDTO> getAgentStatus(@PathVariable String agentId) {
        Agent agent = agentRegistry.getAgent(agentId);
        if (agent == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toStatusDTO(agent));
    }

    /**
     * 启动指定Agent
     */
    @PostMapping("/{agentId}/start")
    public ResponseEntity<Map<String, Object>> startAgent(@PathVariable String agentId) {
        Agent agent = agentRegistry.getAgent(agentId);
        if (agent == null) {
            return ResponseEntity.notFound().build();
        }
        agent.start();
        return ResponseEntity.ok(Map.of("success", true, "status", agent.getStatus().name()));
    }

    /**
     * 停止指定Agent
     */
    @PostMapping("/{agentId}/stop")
    public ResponseEntity<Map<String, Object>> stopAgent(@PathVariable String agentId) {
        Agent agent = agentRegistry.getAgent(agentId);
        if (agent == null) {
            return ResponseEntity.notFound().build();
        }
        agent.stop();
        return ResponseEntity.ok(Map.of("success", true, "status", agent.getStatus().name()));
    }

    /**
     * 获取系统全局指标
     */
    @GetMapping("/metrics")
    public ResponseEntity<AgentRegistry.SystemMetrics> getSystemMetrics() {
        return ResponseEntity.ok(agentRegistry.getSystemMetrics());
    }

    private AgentStatusDTO toStatusDTO(Agent agent) {
        var metrics = agent.getMetrics();
        return AgentStatusDTO.builder()
                .agentId(agent.getAgentId())
                .agentName(agent.getAgentName())
                .agentType(agent.getAgentType().getDisplayName())
                .status(agent.getStatus().getDisplayName())
                .processedEvents(metrics.getProcessedEvents().get())
                .failedEvents(metrics.getFailedEvents().get())
                .activeTasks(metrics.getActiveTasks().get())
                .successRate(metrics.getSuccessRate())
                .avgProcessingTimeMs(metrics.getAvgProcessingTimeMs())
                .uptimeSeconds(metrics.getUptimeSeconds())
                .build();
    }
}
