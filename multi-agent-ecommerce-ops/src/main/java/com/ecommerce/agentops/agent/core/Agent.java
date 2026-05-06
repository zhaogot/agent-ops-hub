package com.ecommerce.agentops.agent.core;

import com.ecommerce.agentops.event.BaseEvent;

import java.util.List;

/**
 * Agent统一接口 - 所有Agent的契约
 *
 * 每个Agent都是一个自治的决策单元，具备:
 * - 感知: 通过事件订阅感知系统状态变化
 * - 决策: 基于感知到的信息做出业务决策
 * - 执行: 执行决策结果，产生新的事件
 */
public interface Agent {

    /**
     * 获取Agent唯一标识
     */
    String getAgentId();

    /**
     * 获取Agent名称（人类可读）
     */
    String getAgentName();

    /**
     * 获取Agent类型
     */
    AgentType getAgentType();

    /**
     * 获取当前状态
     */
    AgentStatus getStatus();

    /**
     * 启动Agent
     */
    void start();

    /**
     * 停止Agent
     */
    void stop();

    /**
     * 处理事件（核心方法）
     * @param event 接收到的事件
     */
    void onEvent(BaseEvent event);

    /**
     * 获取该Agent订阅的事件类型列表
     */
    List<String> getSubscribedEventTypes();

    /**
     * 获取Agent运行指标
     */
    AgentMetrics getMetrics();

    /**
     * Agent类型枚举
     */
    enum AgentType {
        CUSTOMER_SERVICE("客服Agent"),
        MARKETING("营销Agent"),
        OPERATIONS("运营Agent"),
        MONITORING("监控Agent"),
        ORCHESTRATOR("编排Agent");

        private final String displayName;

        AgentType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
