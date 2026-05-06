package com.ecommerce.agentops.agent.monitoring;

import com.ecommerce.agentops.agent.core.Agent;
import com.ecommerce.agentops.agent.core.AgentRegistry;
import com.ecommerce.agentops.agent.core.AgentStatus;
import com.ecommerce.agentops.agent.core.BaseAgent;
import com.ecommerce.agentops.event.BaseEvent;
import com.ecommerce.agentops.event.DomainEvents;
import com.ecommerce.agentops.event.EventBus;
import com.ecommerce.agentops.model.enums.AlertLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 监控Agent - 系统监控与告警
 *
 * 核心职责:
 * 1. 健康监控: 定期检查所有Agent的运行状态
 * 2. 异常检测: 分析业务指标，检测异常模式
 * 3. 告警管理: 收集、聚合和分发告警
 * 4. 性能监控: 追踪系统和Agent性能指标
 * 5. 报表生成: 定期生成运营报表
 */
@Slf4j
@Component
public class MonitoringAgent extends BaseAgent {

    /** 告警历史 */
    private final Deque<Alert> alertHistory = new ConcurrentLinkedDeque<>();

    /** 最大告警历史记录数 */
    private static final int MAX_ALERT_HISTORY = 1000;

    /** Agent注册中心引用 */
    private final AgentRegistry agentRegistry;

    /** 系统启动时间 */
    private final LocalDateTime systemStartTime = LocalDateTime.now();

    /** 指标计数器 */
    private long totalOrdersProcessed = 0;
    private long totalRevenue = 0;
    private long totalAlertsGenerated = 0;

    public MonitoringAgent(EventBus eventBus, AgentRegistry agentRegistry) {
        super(eventBus);
        this.agentRegistry = agentRegistry;
    }

    @Override
    public String getAgentId() {
        return "monitoring-agent";
    }

    @Override
    public String getAgentName() {
        return "智能监控Agent";
    }

    @Override
    public AgentType getAgentType() {
        return AgentType.MONITORING;
    }

    @Override
    public List<String> getSubscribedEventTypes() {
        return List.of(
                DomainEvents.ORDER_CREATED,
                DomainEvents.ORDER_PAID,
                DomainEvents.ORDER_ANOMALY,
                DomainEvents.ANOMALY_DETECTED,
                DomainEvents.PERFORMANCE_ALERT,
                DomainEvents.BUSINESS_METRIC_ALERT,
                DomainEvents.AGENT_STATUS_CHANGED,
                DomainEvents.WORKFLOW_FAILED,
                DomainEvents.CUSTOMER_SERVICE_ESCALATION,
                DomainEvents.STOCK_WARNING,
                DomainEvents.STOCK_DEPLETED
        );
    }

    @Override
    protected void handleEvent(BaseEvent event) {
        switch (event.getEventType()) {
            case DomainEvents.ORDER_CREATED -> handleOrderCreated(event);
            case DomainEvents.ORDER_PAID -> handleOrderPaid(event);
            case DomainEvents.ORDER_ANOMALY -> handleOrderAnomaly(event);
            case DomainEvents.ANOMALY_DETECTED -> handleAnomalyDetected(event);
            case DomainEvents.PERFORMANCE_ALERT -> handlePerformanceAlert(event);
            case DomainEvents.BUSINESS_METRIC_ALERT -> handleBusinessMetricAlert(event);
            case DomainEvents.AGENT_STATUS_CHANGED -> handleAgentStatusChanged(event);
            case DomainEvents.WORKFLOW_FAILED -> handleWorkflowFailed(event);
            case DomainEvents.CUSTOMER_SERVICE_ESCALATION -> handleServiceEscalation(event);
            case DomainEvents.STOCK_WARNING -> handleStockWarning(event);
            case DomainEvents.STOCK_DEPLETED -> handleStockDepleted(event);
            default -> log.warn("未处理的事件类型: {}", event.getEventType());
        }
    }

    /**
     * 定时健康检查 - 每60秒检查一次所有Agent状态
     */
    @Scheduled(fixedRate = 60000)
    public void scheduledHealthCheck() {
        if (getStatus() != AgentStatus.RUNNING) return;

        log.debug("执行Agent健康检查...");

        for (Agent agent : agentRegistry.getAllAgents()) {
            if (agent.getAgentId().equals(getAgentId())) continue;

            AgentStatus status = agent.getStatus();

            // 检查Agent是否异常
            if (status == AgentStatus.ERROR) {
                generateAlert(AlertLevel.CRITICAL, "AGENT_ERROR",
                        String.format("Agent [%s] 处于异常状态", agent.getAgentName()),
                        Map.of("agentId", agent.getAgentId(), "status", status.name()));
            }

            // 检查Agent是否长时间未处理事件
            var metrics = agent.getMetrics();
            if (metrics.getLastEventTime() != null) {
                long minutesSinceLastEvent = java.time.Duration.between(
                        metrics.getLastEventTime(), LocalDateTime.now()).toMinutes();
                if (minutesSinceLastEvent > 30 && status == AgentStatus.RUNNING) {
                    generateAlert(AlertLevel.WARNING, "AGENT_IDLE",
                            String.format("Agent [%s] 已%d分钟未处理事件",
                                    agent.getAgentName(), minutesSinceLastEvent),
                            Map.of("agentId", agent.getAgentId()));
                }
            }

            // 检查失败率
            double failRate = 100 - metrics.getSuccessRate();
            if (failRate > 20 && metrics.getTotalEvents() > 10) {
                generateAlert(AlertLevel.ERROR, "HIGH_FAIL_RATE",
                        String.format("Agent [%s] 失败率过高: %.1f%%", agent.getAgentName(), failRate),
                        Map.of("agentId", agent.getAgentId(), "failRate", failRate));
            }
        }
    }

    /**
     * 定时报表 - 每5分钟生成系统状态快照
     */
    @Scheduled(fixedRate = 300000)
    public void scheduledReport() {
        if (getStatus() != AgentStatus.RUNNING) return;

        var systemMetrics = agentRegistry.getSystemMetrics();

        log.info("========== 系统状态报表 ==========");
        log.info("运行中Agent: {}/{}", systemMetrics.getRunningAgents(), systemMetrics.getTotalAgents());
        log.info("总处理事件: {}", systemMetrics.getTotalProcessedEvents());
        log.info("总失败事件: {}", systemMetrics.getTotalFailedEvents());
        log.info("总订单处理: {}", totalOrdersProcessed);
        log.info("今日告警数: {}", totalAlertsGenerated);
        log.info("==================================");
    }

    private void handleOrderCreated(BaseEvent event) {
        totalOrdersProcessed++;
        String orderId = event.getPayload("orderId");
        log.debug("订单创建监控: orderId={}", orderId);
    }

    private void handleOrderPaid(BaseEvent event) {
        Long amount = event.getPayload("amount");
        if (amount != null) {
            totalRevenue += amount;
        }

        // 大额订单告警
        if (amount != null && amount > 10000) {
            generateAlert(AlertLevel.INFO, "LARGE_ORDER",
                    String.format("大额订单: 金额=%d", amount),
                    Map.of("orderId", event.getPayload("orderId").toString(), "amount", amount));
        }
    }

    private void handleOrderAnomaly(BaseEvent event) {
        String orderId = event.getPayload("orderId");
        String anomalyType = event.getPayload("anomalyType");

        generateAlert(AlertLevel.WARNING, "ORDER_ANOMALY",
                String.format("订单异常: orderId=%s, type=%s", orderId, anomalyType),
                Map.of("orderId", orderId, "anomalyType", anomalyType));
    }

    private void handleAnomalyDetected(BaseEvent event) {
        String anomalyType = event.getPayload("anomalyType");
        String description = event.getPayload("description");

        generateAlert(AlertLevel.ERROR, anomalyType, description, event.getPayload());
    }

    private void handlePerformanceAlert(BaseEvent event) {
        String metric = event.getPayload("metric");
        String value = event.getPayload("value");

        generateAlert(AlertLevel.WARNING, "PERFORMANCE",
                String.format("性能告警: %s=%s", metric, value), event.getPayload());
    }

    private void handleBusinessMetricAlert(BaseEvent event) {
        String metricType = event.getPayload("metricType");
        generateAlert(AlertLevel.WARNING, metricType,
                "业务指标告警: " + metricType, event.getPayload());
    }

    private void handleAgentStatusChanged(BaseEvent event) {
        String agentId = event.getPayload("agentId");
        String newStatus = event.getPayload("newStatus");

        log.info("Agent状态变更: agentId={}, newStatus={}", agentId, newStatus);

        if ("ERROR".equals(newStatus)) {
            generateAlert(AlertLevel.CRITICAL, "AGENT_DOWN",
                    String.format("Agent异常: agentId=%s", agentId),
                    Map.of("agentId", agentId, "status", newStatus));
        }
    }

    private void handleWorkflowFailed(BaseEvent event) {
        String workflowId = event.getPayload("workflowId");
        String reason = event.getPayload("reason");

        generateAlert(AlertLevel.ERROR, "WORKFLOW_FAILED",
                String.format("工作流失败: workflowId=%s, reason=%s", workflowId, reason),
                event.getPayload());
    }

    private void handleServiceEscalation(BaseEvent event) {
        String userId = event.getPayload("userId");
        String sentiment = event.getPayload("sentiment");

        generateAlert(AlertLevel.WARNING, "SERVICE_ESCALATION",
                String.format("客服升级: userId=%s, sentiment=%s", userId, sentiment),
                event.getPayload());
    }

    private void handleStockWarning(BaseEvent event) {
        String productId = event.getPayload("productId");
        Integer currentStock = event.getPayload("currentStock");

        generateAlert(AlertLevel.WARNING, "STOCK_LOW",
                String.format("库存预警: productId=%s, stock=%d", productId, currentStock),
                event.getPayload());
    }

    private void handleStockDepleted(BaseEvent event) {
        String productId = event.getPayload("productId");

        generateAlert(AlertLevel.CRITICAL, "STOCK_OUT",
                String.format("库存耗尽: productId=%s", productId),
                event.getPayload());
    }

    /**
     * 生成告警
     */
    private void generateAlert(AlertLevel level, String alertType, String message,
                                Map<String, Object> context) {
        Alert alert = new Alert(
                UUID.randomUUID().toString().substring(0, 8),
                level, alertType, message, context, LocalDateTime.now()
        );

        alertHistory.addFirst(alert);
        totalAlertsGenerated++;

        // 控制历史记录大小
        while (alertHistory.size() > MAX_ALERT_HISTORY) {
            alertHistory.removeLast();
        }

        // 根据告警级别输出不同日志
        switch (level) {
            case CRITICAL -> log.error("【严重告警】[{}] {}", alertType, message);
            case ERROR -> log.error("【告警】[{}] {}", alertType, message);
            case WARNING -> log.warn("【预警】[{}] {}", alertType, message);
            case INFO -> log.info("【信息】[{}] {}", alertType, message);
        }
    }

    /**
     * 获取最近的告警列表
     */
    public List<Alert> getRecentAlerts(int count) {
        return alertHistory.stream().limit(count).toList();
    }

    /**
     * 获取按级别过滤的告警
     */
    public List<Alert> getAlertsByLevel(AlertLevel level) {
        return alertHistory.stream()
                .filter(a -> a.level() == level)
                .toList();
    }

    /**
     * 获取系统概览
     */
    public Map<String, Object> getSystemOverview() {
        var systemMetrics = agentRegistry.getSystemMetrics();
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("systemUptime", java.time.Duration.between(systemStartTime, LocalDateTime.now()).toMinutes() + " 分钟");
        overview.put("totalAgents", systemMetrics.getTotalAgents());
        overview.put("runningAgents", systemMetrics.getRunningAgents());
        overview.put("totalProcessedEvents", systemMetrics.getTotalProcessedEvents());
        overview.put("totalFailedEvents", systemMetrics.getTotalFailedEvents());
        overview.put("totalOrdersProcessed", totalOrdersProcessed);
        overview.put("totalRevenue", totalRevenue);
        overview.put("totalAlerts", totalAlertsGenerated);
        overview.put("criticalAlerts", alertHistory.stream()
                .filter(a -> a.level() == AlertLevel.CRITICAL).count());
        return overview;
    }

    /**
     * 告警记录
     */
    public record Alert(
            String alertId,
            AlertLevel level,
            String alertType,
            String message,
            Map<String, Object> context,
            LocalDateTime timestamp
    ) {}
}
