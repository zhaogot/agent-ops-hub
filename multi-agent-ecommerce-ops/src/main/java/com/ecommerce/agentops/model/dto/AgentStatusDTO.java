package com.ecommerce.agentops.model.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Agent状态DTO - 用于API响应
 */
@Data
@Builder
public class AgentStatusDTO {
    private String agentId;
    private String agentName;
    private String agentType;
    private String status;
    private long processedEvents;
    private long failedEvents;
    private long activeTasks;
    private double successRate;
    private double avgProcessingTimeMs;
    private long uptimeSeconds;
}
