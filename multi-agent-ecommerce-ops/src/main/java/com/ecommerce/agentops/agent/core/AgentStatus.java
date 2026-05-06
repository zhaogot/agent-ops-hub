package com.ecommerce.agentops.agent.core;

import lombok.Getter;

/**
 * Agent状态枚举
 * Agent生命周期: CREATED -> STARTING -> RUNNING -> STOPPING -> STOPPED
 * 异常状态: ERROR
 */
@Getter
public enum AgentStatus {

    CREATED("已创建", "Agent实例已创建但尚未启动"),
    STARTING("启动中", "Agent正在初始化"),
    RUNNING("运行中", "Agent正常运行，可处理事件"),
    PAUSED("已暂停", "Agent暂时停止处理事件"),
    STOPPING("停止中", "Agent正在优雅关闭"),
    STOPPED("已停止", "Agent已完全停止"),
    ERROR("异常", "Agent运行出现错误");

    private final String displayName;
    private final String description;

    AgentStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
}
