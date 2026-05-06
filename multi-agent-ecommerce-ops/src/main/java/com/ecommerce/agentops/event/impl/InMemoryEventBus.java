package com.ecommerce.agentops.event.impl;

import com.ecommerce.agentops.event.BaseEvent;
import com.ecommerce.agentops.event.EventBus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 内存事件总线实现
 *
 * 基于优先级队列的事件总线，支持:
 * - 事件优先级排序（CRITICAL > HIGH > NORMAL > LOW）
 * - 异步事件分发
 * - 多订阅者支持（同一事件类型可被多个Agent订阅）
 * - 事件消费统计
 *
 * 生产环境可替换为Kafka/RabbitMQ实现，接口不变
 */
@Slf4j
@Component
public class InMemoryEventBus implements EventBus {

    /** 事件类型 -> 订阅者列表 映射 */
    private final ConcurrentMap<String, List<Subscription>> subscriptions = new ConcurrentHashMap<>();

    /** 优先级事件队列 */
    private final PriorityBlockingQueue<BaseEvent> eventQueue;

    /** 异步执行器 */
    private final Executor executor;

    /** 统计计数器 */
    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);

    /** 运行标志 */
    private volatile boolean running = true;

    public InMemoryEventBus(@Qualifier("eventExecutor") Executor executor) {
        this.executor = executor;
        this.eventQueue = new PriorityBlockingQueue<>(1000,
                Comparator.comparing(BaseEvent::getPriority).reversed()
                        .thenComparing(BaseEvent::getTimestamp));

        // 启动事件分发线程
        startDispatchLoop();
    }

    @Override
    public void publish(BaseEvent event) {
        log.debug("发布事件: type={}, source={}, target={}, id={}",
                event.getEventType(), event.getSourceAgentId(),
                event.getTargetAgentId(), event.getEventId());
        eventQueue.offer(event);
    }

    @Override
    public void subscribe(String eventType, String subscriberId, Consumer<BaseEvent> handler) {
        subscriptions.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                .add(new Subscription(subscriberId, handler));
        log.info("订阅注册: eventType={}, subscriber={}", eventType, subscriberId);
    }

    @Override
    public void unsubscribe(String eventType, String subscriberId) {
        List<Subscription> subs = subscriptions.get(eventType);
        if (subs != null) {
            subs.removeIf(s -> s.subscriberId.equals(subscriberId));
            log.info("订阅取消: eventType={}, subscriber={}", eventType, subscriberId);
        }
    }

    @Override
    public long getPendingEventCount() {
        return eventQueue.size();
    }

    @Override
    public long getProcessedEventCount() {
        return processedCount.get();
    }

    /**
     * 启动事件分发循环
     */
    private void startDispatchLoop() {
        Thread dispatchThread = new Thread(() -> {
            log.info("事件总线分发线程启动");
            while (running) {
                try {
                    BaseEvent event = eventQueue.poll(1, TimeUnit.SECONDS);
                    if (event != null) {
                        dispatchEvent(event);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    log.error("事件分发异常", e);
                }
            }
        }, "event-dispatcher");
        dispatchThread.setDaemon(true);
        dispatchThread.start();
    }

    /**
     * 将事件分发给所有匹配的订阅者
     */
    private void dispatchEvent(BaseEvent event) {
        List<Subscription> subscribers = subscriptions.get(event.getEventType());

        if (subscribers == null || subscribers.isEmpty()) {
            log.trace("事件无订阅者: type={}, id={}", event.getEventType(), event.getEventId());
            processedCount.incrementAndGet();
            return;
        }

        // 如果事件有目标Agent，只发送给目标
        if (event.getTargetAgentId() != null) {
            subscribers.stream()
                    .filter(s -> s.subscriberId.equals(event.getTargetAgentId()))
                    .forEach(s -> dispatchToSubscriber(s, event));
        } else {
            // 广播给所有订阅者
            subscribers.forEach(s -> dispatchToSubscriber(s, event));
        }

        processedCount.incrementAndGet();
    }

    /**
     * 异步分发事件给单个订阅者
     */
    private void dispatchToSubscriber(Subscription subscription, BaseEvent event) {
        executor.execute(() -> {
            try {
                log.debug("事件分发: type={} -> subscriber={}", event.getEventType(), subscription.subscriberId);
                subscription.handler.accept(event);
            } catch (Exception e) {
                errorCount.incrementAndGet();
                log.error("事件处理异常: type={}, subscriber={}, error={}",
                        event.getEventType(), subscription.subscriberId, e.getMessage());
            }
        });
    }

    /**
     * 内部订阅者封装
     */
    private static class Subscription {
        final String subscriberId;
        final Consumer<BaseEvent> handler;

        Subscription(String subscriberId, Consumer<BaseEvent> handler) {
            this.subscriberId = subscriberId;
            this.handler = handler;
        }
    }
}
