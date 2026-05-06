package com.ecommerce.agentops.event;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 事件基类 - 所有Agent间通信事件的根类
 *
 * 设计原则:
 * - 事件是不可变的(immutable)，一旦创建不应修改
 * - 事件携带完整的上下文信息，接收方无需反查
 * - 事件通过EventBus分发，实现Agent间松耦合通信
 */
@Data
public abstract class BaseEvent {

    /** 事件唯一ID */
    private final String eventId;

    /** 事件类型标识 */
    private final String eventType;

    /** 事件产生时间 */
    private final LocalDateTime timestamp;

    /** 事件来源Agent */
    private final String sourceAgentId;

    /** 目标Agent（null表示广播事件） */
    private final String targetAgentId;

    /** 事件优先级 */
    private final EventPriority priority;

    /** 事件携带的业务数据 */
    private final Map<String, Object> payload;

    protected BaseEvent(String eventType, String sourceAgentId, String targetAgentId, EventPriority priority) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = eventType;
        this.timestamp = LocalDateTime.now();
        this.sourceAgentId = sourceAgentId;
        this.targetAgentId = targetAgentId;
        this.priority = priority;
        this.payload = new HashMap<>();
    }

    protected BaseEvent(String eventType, String sourceAgentId) {
        this(eventType, sourceAgentId, null, EventPriority.NORMAL);
    }

    /**
     * 向事件添加业务数据
     */
    public BaseEvent withPayload(String key, Object value) {
        this.payload.put(key, value);
        return this;
    }

    /**
     * 获取指定类型的payload值
     */
    @SuppressWarnings("unchecked")
    public <T> T getPayload(String key) {
        return (T) payload.get(key);
    }

    /**
     * 事件优先级枚举
     */
    public enum EventPriority {
        LOW,      // 低优先级 - 可延迟处理
        NORMAL,   // 正常优先级
        HIGH,     // 高优先级 - 尽快处理
        CRITICAL  // 紧急 - 立即处理
    }
}
