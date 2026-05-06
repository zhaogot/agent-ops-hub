package com.ecommerce.agentops.controller;

import com.ecommerce.agentops.agent.orchestrator.AgentOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 编排引擎API
 */
@RestController
@RequestMapping("/api/orchestrator")
@RequiredArgsConstructor
public class OrchestratorController {

    private final AgentOrchestrator orchestrator;

    /**
     * 获取工作流模板列表
     */
    @GetMapping("/templates")
    public ResponseEntity<Map<String, AgentOrchestrator.WorkflowDefinition>> getTemplates() {
        return ResponseEntity.ok(orchestrator.getWorkflowTemplates());
    }

    /**
     * 获取活跃工作流列表
     */
    @GetMapping("/workflows")
    public ResponseEntity<Map<String, AgentOrchestrator.WorkflowInstance>> getWorkflows() {
        return ResponseEntity.ok(orchestrator.getActiveWorkflows());
    }

    /**
     * 获取工作流详情
     */
    @GetMapping("/workflows/{instanceId}")
    public ResponseEntity<AgentOrchestrator.WorkflowInstance> getWorkflow(
            @PathVariable String instanceId) {
        AgentOrchestrator.WorkflowInstance instance = orchestrator.getWorkflow(instanceId);
        if (instance == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(instance);
    }

    /**
     * 手动启动工作流
     */
    @PostMapping("/workflows/start/{templateId}")
    public ResponseEntity<Map<String, Object>> startWorkflow(
            @PathVariable String templateId,
            @RequestBody(required = false) Map<String, Object> params) {
        String instanceId = orchestrator.startWorkflow(templateId, params);
        if (instanceId == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "工作流模板不存在: " + templateId
            ));
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "workflowId", instanceId,
                "templateId", templateId
        ));
    }
}
