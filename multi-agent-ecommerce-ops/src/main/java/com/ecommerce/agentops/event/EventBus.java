package com.ecommerce.agentops.event;

import java.util.function.Consumer;

/**
 * 事件总线接口 - Agent间通信的核心基础设施
 *
 * 事件总线是多Agent系统的神经网络:
 * - 发布/订阅模式: Agent发布事件，其他Agent订阅感兴趣的话题
 * - 点对点通信: 支持指定目标Agent的直接通信
 * - 优先级队列: 高优先级事件优先处理
 * - 异步非阻塞: 事件发布后立即返回，不阻塞调用方
 */
public interface EventBus {

    /**
     * 发布事件到总线
     * @param event 待发布的事件
     */
    void publish(BaseEvent event);

    /**
     * 订阅指定类型的事件
     * @param eventType 事件类型
     * @param subscriberId 订阅者ID
     * @param handler 事件处理器
     */
    void subscribe(String eventType, String subscriberId, Consumer<BaseEvent> handler);

    /**
     * 取消订阅
     * @param eventType 事件类型
     * @param subscriberId 订阅者ID
     */
    void unsubscribe(String eventType, String subscriberId);

    /**
     * 获取待处理事件数量
     */
    long getPendingEventCount();

    /**
     * 获取已处理事件总数
     */
    long getProcessedEventCount();
}
