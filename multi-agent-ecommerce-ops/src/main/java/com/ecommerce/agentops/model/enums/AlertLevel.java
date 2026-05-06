package com.ecommerce.agentops.model.enums;

import lombok.Getter;

@Getter
public enum AlertLevel {
    INFO("信息"),
    WARNING("警告"),
    ERROR("错误"),
    CRITICAL("严重");

    private final String displayName;

    AlertLevel(String displayName) {
        this.displayName = displayName;
    }
}
