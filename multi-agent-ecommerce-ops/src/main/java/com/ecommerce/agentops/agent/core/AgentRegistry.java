package com.ecommerce.agentops.agent.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Agent注册中心
 *
 * 管理所有Agent实例的生命周期:
 * - 注册/注销Agent
 * - 按类型/状态查询Agent
 * - 批量启停操作
 */
@Slf4j
@Component
public class AgentRegistry {

    /** agentId -> Agent 实例映射 */
    private final Map<String, Agent> agents = new ConcurrentHashMap<>();

    /**
     * 注册Agent
     */
    public void register(Agent agent) {
        agents.put(agent.getAgentId(), agent);
        log.info("Agent注册成功: id={}, type={}, name={}",
                agent.getAgentId(), agent.getAgentType(), agent.getAgentName());
    }

    /**
     * 注销Agent
     */
    public void unregister(String agentId) {
        Agent removed = agents.remove(agentId);
        if (removed != null) {
            log.info("Agent已注销: id={}", agentId);
        }
    }

    /**
     * 获取指定Agent
     */
    public Agent getAgent(String agentId) {
        return agents.get(agentId);
    }

    /**
     * 获取所有Agent
     */
    public Collection<Agent> getAllAgents() {
        return Collections.unmodifiableCollection(agents.values());
    }

    /**
     * 按类型获取Agent
     */
    public List<Agent> getAgentsByType(Agent.AgentType type) {
        return agents.values().stream()
                .filter(a -> a.getAgentType() == type)
                .collect(Collectors.toList());
    }

    /**
     * 按状态获取Agent
     */
    public List<Agent> getAgentsByStatus(AgentStatus status) {
        return agents.values().stream()
                .filter(a -> a.getStatus() == status)
                .collect(Collectors.toList());
    }

    /**
     * 启动所有已注册的Agent
     */
    public void startAll() {
        log.info("启动所有Agent (共{}个)...", agents.size());
        agents.values().forEach(agent -> {
            try {
                agent.start();
            } catch (Exception e) {
                log.error("Agent启动失败: id={}", agent.getAgentId(), e);
            }
        });
        log.info("所有Agent启动完成");
    }

    /**
     * 停止所有Agent
     */
    public void stopAll() {
        log.info("停止所有Agent...");
        agents.values().forEach(agent -> {
            try {
                if (agent.getStatus() == AgentStatus.RUNNING) {
                    agent.stop();
                }
            } catch (Exception e) {
                log.error("Agent停止失败: id={}", agent.getAgentId(), e);
            }
        });
        log.info("所有Agent已停止");
    }

    /**
     * 获取系统全局指标
     */
    public SystemMetrics getSystemMetrics() {
        SystemMetrics systemMetrics = new SystemMetrics();
        systemMetrics.totalAgents = agents.size();
        systemMetrics.runningAgents = (int) agents.values().stream()
                .filter(a -> a.getStatus() == AgentStatus.RUNNING).count();
        systemMetrics.totalProcessedEvents = agents.values().stream()
                .mapToLong(a -> a.getMetrics().getProcessedEvents().get()).sum();
        systemMetrics.totalFailedEvents = agents.values().stream()
                .mapToLong(a -> a.getMetrics().getFailedEvents().get()).sum();
        systemMetrics.agentDetails = agents.values().stream()
                .map(a -> new AgentDetail(a.getAgentId(), a.getAgentName(),
                        a.getAgentType(), a.getStatus(), a.getMetrics()))
                .collect(Collectors.toList());
        return systemMetrics;
    }

    /**
     * 系统全局指标DTO
     */
    @lombok.Data
    public static class SystemMetrics {
        private int totalAgents;
        private int runningAgents;
        private long totalProcessedEvents;
        private long totalFailedEvents;
        private List<AgentDetail> agentDetails;
    }

    /**
     * Agent详情DTO
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class AgentDetail {
        private String agentId;
        private String agentName;
        private Agent.AgentType agentType;
        private AgentStatus status;
        private AgentMetrics metrics;
    }
}
