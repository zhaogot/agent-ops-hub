package com.ecommerce.agentops.agent.core;

import com.ecommerce.agentops.event.EventBus;
import com.ecommerce.agentops.event.BaseEvent;
import lombok.Getter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent运行上下文
 *
 * 每个Agent拥有独立的上下文，包含:
 * - 共享服务引用（EventBus等）
 * - Agent私有的运行时状态
 * - 上下文级缓存
 *
 * 上下文是Agent与外部世界交互的桥梁
 */
@Getter
public class AgentContext {

    /** Agent所属的Agent ID */
    private final String agentId;

    /** 事件总线引用 */
    private final EventBus eventBus;

    /** Agent私有的上下文数据 */
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    public AgentContext(String agentId, EventBus eventBus) {
        this.agentId = agentId;
        this.eventBus = eventBus;
    }

    /**
     * 通过事件总线发布事件
     */
    public void publishEvent(BaseEvent event) {
        eventBus.publish(event);
    }

    /**
     * 设置上下文属性
     */
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    /**
     * 获取上下文属性
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    /**
     * 获取上下文属性（带默认值）
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key, T defaultValue) {
        return (T) attributes.getOrDefault(key, defaultValue);
    }

    /**
     * 移除上下文属性
     */
    public void removeAttribute(String key) {
        attributes.remove(key);
    }

    /**
     * 清除所有上下文
     */
    public void clear() {
        attributes.clear();
    }
}
