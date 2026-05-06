package com.ecommerce.agentops.agent.core;

import lombok.Data;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Agent运行指标
 * 追踪每个Agent的处理效率和健康状态
 */
@Data
public class AgentMetrics {

    /** 已处理事件总数 */
    private final AtomicLong processedEvents = new AtomicLong(0);

    /** 处理失败事件数 */
    private final AtomicLong failedEvents = new AtomicLong(0);

    /** 当前正在处理的任务数 */
    private final AtomicLong activeTasks = new AtomicLong(0);

    /** 最后一次处理事件的时间 */
    private volatile LocalDateTime lastEventTime;

    /** Agent启动时间 */
    private volatile LocalDateTime startTime;

    /** 平均处理耗时(毫秒) */
    private volatile double avgProcessingTimeMs = 0;

    /** 总处理耗时(用于计算平均值) */
    private final AtomicLong totalProcessingTimeMs = new AtomicLong(0);

    public void recordEventProcessed(long durationMs) {
        processedEvents.incrementAndGet();
        totalProcessingTimeMs.addAndGet(durationMs);
        lastEventTime = LocalDateTime.now();
        avgProcessingTimeMs = (double) totalProcessingTimeMs.get() / processedEvents.get();
    }

    public void recordEventFailed() {
        failedEvents.incrementAndGet();
    }

    public void incrementActiveTasks() {
        activeTasks.incrementAndGet();
    }

    public void decrementActiveTasks() {
        activeTasks.decrementAndGet();
    }

    public long getUptimeSeconds() {
        if (startTime == null) return 0;
        return Duration.between(startTime, LocalDateTime.now()).getSeconds();
    }

    public long getTotalEvents() {
        return processedEvents.get() + failedEvents.get();
    }

    public double getSuccessRate() {
        long total = getTotalEvents();
        return total == 0 ? 0 : (double) processedEvents.get() / total * 100;
    }
}
