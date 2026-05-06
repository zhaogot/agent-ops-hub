package com.ecommerce.agentops.agent.core;

import com.ecommerce.agentops.event.BaseEvent;
import com.ecommerce.agentops.event.EventBus;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Agent抽象基类
 *
 * 提供Agent生命周期管理、事件订阅、指标收集等通用能力。
 * 子类只需关注:
 * - getAgentId() / getAgentName() / getAgentType() - 标识
 * - getSubscribedEventTypes() - 订阅哪些事件
 * - handleEvent(BaseEvent) - 如何处理事件（核心业务逻辑）
 * - onStart() / onStop() - 可选的初始化/销毁钩子
 */
@Slf4j
@Getter
public abstract class BaseAgent implements Agent {

    private final AtomicReference<AgentStatus> status = new AtomicReference<>(AgentStatus.CREATED);
    private final AgentMetrics metrics = new AgentMetrics();
    protected AgentContext context;

    protected final EventBus eventBus;

    protected BaseAgent(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @PostConstruct
    public void initialize() {
        this.context = new AgentContext(getAgentId(), eventBus);
        log.info("[{}] Agent实例已创建", getAgentId());
    }

    @Override
    public void start() {
        if (!status.compareAndSet(AgentStatus.CREATED, AgentStatus.STARTING)
                && !status.compareAndSet(AgentStatus.STOPPED, AgentStatus.STARTING)) {
            log.warn("[{}] Agent无法从{}状态启动", getAgentId(), status.get());
            return;
        }

        try {
            MDC.put("agentId", getAgentId());
            log.info("[{}] Agent正在启动...", getAgentId());

            // 注册事件订阅
            subscribeToEvents();

            // 子类初始化钩子
            onStart();

            status.set(AgentStatus.RUNNING);
            metrics.setStartTime(LocalDateTime.now());
            log.info("[{}] Agent启动成功，已订阅{}类事件", getAgentId(), getSubscribedEventTypes().size());

        } catch (Exception e) {
            status.set(AgentStatus.ERROR);
            log.error("[{}] Agent启动失败", getAgentId(), e);
            throw new RuntimeException("Agent启动失败: " + getAgentId(), e);
        } finally {
            MDC.remove("agentId");
        }
    }

    @Override
    public void stop() {
        if (!status.compareAndSet(AgentStatus.RUNNING, AgentStatus.STOPPING)
                && !status.compareAndSet(AgentStatus.PAUSED, AgentStatus.STOPPING)) {
            log.warn("[{}] Agent无法从{}状态停止", getAgentId(), status.get());
            return;
        }

        try {
            MDC.put("agentId", getAgentId());
            log.info("[{}] Agent正在停止...", getAgentId());

            // 取消事件订阅
            unsubscribeFromEvents();

            // 子类销毁钩子
            onStop();

            context.clear();
            status.set(AgentStatus.STOPPED);
            log.info("[{}] Agent已停止", getAgentId());

        } catch (Exception e) {
            status.set(AgentStatus.ERROR);
            log.error("[{}] Agent停止异常", getAgentId(), e);
        } finally {
            MDC.remove("agentId");
        }
    }

    @Override
    public void onEvent(BaseEvent event) {
        if (status.get() != AgentStatus.RUNNING) {
            log.debug("[{}] Agent未在运行状态，忽略事件: {}", getAgentId(), event.getEventType());
            return;
        }

        MDC.put("agentId", getAgentId());
        long startTime = System.currentTimeMillis();

        try {
            metrics.incrementActiveTasks();
            log.debug("[{}] 处理事件: type={}, eventId={}", getAgentId(), event.getEventType(), event.getEventId());

            // 调用子类的事件处理逻辑
            handleEvent(event);

            long duration = System.currentTimeMillis() - startTime;
            metrics.recordEventProcessed(duration);
            log.debug("[{}] 事件处理完成: type={}, 耗时={}ms", getAgentId(), event.getEventType(), duration);

        } catch (Exception e) {
            metrics.recordEventFailed();
            log.error("[{}] 事件处理异常: type={}, eventId={}, error={}",
                    getAgentId(), event.getEventType(), event.getEventId(), e.getMessage());

            // 发布异常事件给监控Agent
            publishErrorEvent(event, e);

        } finally {
            metrics.decrementActiveTasks();
            MDC.remove("agentId");
        }
    }

    /**
     * 子类实现：处理接收到的事件
     */
    protected abstract void handleEvent(BaseEvent event);

    /**
     * 子类可覆盖：启动时初始化逻辑
     */
    protected void onStart() {}

    /**
     * 子类可覆盖：停止时清理逻辑
     */
    protected void onStop() {}

    /**
     * 注册事件订阅
     */
    private void subscribeToEvents() {
        for (String eventType : getSubscribedEventTypes()) {
            eventBus.subscribe(eventType, getAgentId(), this::onEvent);
        }
    }

    /**
     * 取消事件订阅
     */
    private void unsubscribeFromEvents() {
        for (String eventType : getSubscribedEventTypes()) {
            eventBus.unsubscribe(eventType, getAgentId());
        }
    }

    /**
     * 发布错误事件给监控Agent
     */
    private void publishErrorEvent(BaseEvent originalEvent, Exception error) {
        try {
            BaseEvent errorEvent = new BaseEvent("agent.error", getAgentId()) {};
            errorEvent.withPayload("originalEventType", originalEvent.getEventType())
                    .withPayload("originalEventId", originalEvent.getEventId())
                    .withPayload("errorMessage", error.getMessage())
                    .withPayload("agentId", getAgentId());
            eventBus.publish(errorEvent);
        } catch (Exception e) {
            log.error("[{}] 发布错误事件失败", getAgentId(), e);
        }
    }

    @Override
    public AgentStatus getStatus() {
        return status.get();
    }

    /**
     * 辅助方法：创建事件
     */
    protected BaseEvent createEvent(String eventType, String targetAgentId, BaseEvent.EventPriority priority) {
        return new BaseEvent(eventType, getAgentId(), targetAgentId, priority) {};
    }

    protected BaseEvent createEvent(String eventType) {
        return new BaseEvent(eventType, getAgentId()) {};
    }
}
