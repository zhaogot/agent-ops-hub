package com.ecommerce.agentops.agent.orchestrator;

import com.ecommerce.agentops.event.BaseEvent;
import com.ecommerce.agentops.event.DomainEvents;
import com.ecommerce.agentops.event.EventBus;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Agent编排引擎 - 复杂业务流程的多Agent协调
 *
 * 职责:
 * 1. 工作流定义: 定义跨Agent的业务流程
 * 2. 流程调度: 按照流程定义协调多个Agent
 * 3. 状态跟踪: 跟踪工作流执行进度
 * 4. 异常处理: 工作流级别的错误处理和重试
 */
@Slf4j
@Component
public class AgentOrchestrator {

    private final EventBus eventBus;

    /** 活跃的工作流实例 */
    private final Map<String, WorkflowInstance> activeWorkflows = new ConcurrentHashMap<>();

    /** 工作流模板 */
    private final Map<String, WorkflowDefinition> workflowTemplates = new LinkedHashMap<>();

    /** 工作流ID生成器 */
    private final AtomicInteger workflowCounter = new AtomicInteger(0);

    public AgentOrchestrator(EventBus eventBus) {
        this.eventBus = eventBus;
        initWorkflowTemplates();
        subscribeToEvents();
    }

    /**
     * 初始化预定义的工作流模板
     */
    private void initWorkflowTemplates() {
        // 工作流1: 新订单处理流程
        workflowTemplates.put("new-order-processing", new WorkflowDefinition(
                "new-order-processing",
                "新订单处理流程",
                Arrays.asList(
                        new WorkflowStep("verify-inventory", "operations-agent", "验证库存"),
                        new WorkflowStep("apply-coupon", "marketing-agent", "应用优惠券"),
                        new WorkflowStep("calculate-price", "operations-agent", "计算价格"),
                        new WorkflowStep("send-confirmation", "customer-service-agent", "发送确认")
                )
        ));

        // 工作流2: 退款处理流程
        workflowTemplates.put("refund-processing", new WorkflowDefinition(
                "refund-processing",
                "退款处理流程",
                Arrays.asList(
                        new WorkflowStep("validate-refund", "customer-service-agent", "验证退款资格"),
                        new WorkflowStep("process-refund", "operations-agent", "处理退款"),
                        new WorkflowStep("restore-inventory", "operations-agent", "恢复库存"),
                        new WorkflowStep("issue-compensation", "marketing-agent", "发放补偿券"),
                        new WorkflowStep("notify-customer", "customer-service-agent", "通知客户")
                )
        ));

        // 工作流3: 用户流失挽回流程
        workflowTemplates.put("churn-recovery", new WorkflowDefinition(
                "churn-recovery",
                "用户流失挽回流程",
                Arrays.asList(
                        new WorkflowStep("analyze-risk", "marketing-agent", "分析流失风险"),
                        new WorkflowStep("issue-coupon", "marketing-agent", "发放挽回券"),
                        new WorkflowStep("send-notification", "marketing-agent", "发送召回通知"),
                        new WorkflowStep("track-result", "monitoring-agent", "追踪效果")
                )
        ));

        // 工作流4: 库存补货流程
        workflowTemplates.put("stock-replenishment", new WorkflowDefinition(
                "stock-replenishment",
                "库存补货流程",
                Arrays.asList(
                        new WorkflowStep("check-stock", "operations-agent", "检查库存"),
                        new WorkflowStep("create-purchase-order", "operations-agent", "创建采购单"),
                        new WorkflowStep("notify-supplier", "operations-agent", "通知供应商"),
                        new WorkflowStep("monitor-delivery", "monitoring-agent", "监控到货")
                )
        ));

        log.info("工作流模板初始化完成: {}个模板", workflowTemplates.size());
    }

    /**
     * 订阅工作流相关事件
     */
    private void subscribeToEvents() {
        eventBus.subscribe(DomainEvents.WORKFLOW_STEP_COMPLETED, "orchestrator",
                this::handleStepCompleted);
        eventBus.subscribe(DomainEvents.ORDER_CREATED, "orchestrator",
                this::handleNewOrder);
        eventBus.subscribe(DomainEvents.ORDER_REFUND_REQUEST, "orchestrator",
                this::handleRefundRequest);
        eventBus.subscribe(DomainEvents.CHURN_RISK_DETECTED, "orchestrator",
                this::handleChurnRisk);
        eventBus.subscribe(DomainEvents.STOCK_DEPLETED, "orchestrator",
                this::handleStockDepleted);
    }

    /**
     * 启动工作流
     */
    public String startWorkflow(String templateId, Map<String, Object> params) {
        WorkflowDefinition template = workflowTemplates.get(templateId);
        if (template == null) {
            log.error("工作流模板不存在: {}", templateId);
            return null;
        }

        String instanceId = "wf-" + workflowCounter.incrementAndGet();

        WorkflowInstance instance = new WorkflowInstance();
        instance.setInstanceId(instanceId);
        instance.setTemplateId(templateId);
        instance.setTemplateName(template.getName());
        instance.setSteps(new ArrayList<>(template.getSteps()));
        instance.setCurrentStepIndex(0);
        instance.setStatus(WorkflowStatus.RUNNING);
        instance.setParams(params != null ? params : new HashMap<>());
        instance.setStartedAt(LocalDateTime.now());
        instance.setHistory(new ArrayList<>());

        activeWorkflows.put(instanceId, instance);

        log.info("工作流启动: instanceId={}, template={}, steps={}",
                instanceId, template.getName(), template.getSteps().size());

        // 发布工作流启动事件
        BaseEvent startEvent = new BaseEvent(DomainEvents.WORKFLOW_STARTED, "orchestrator") {};
        startEvent.withPayload("workflowId", instanceId)
                .withPayload("templateId", templateId)
                .withPayload("templateName", template.getName());
        eventBus.publish(startEvent);

        // 执行第一个步骤
        executeCurrentStep(instance);

        return instanceId;
    }

    /**
     * 执行当前步骤
     */
    private void executeCurrentStep(WorkflowInstance instance) {
        if (instance.getCurrentStepIndex() >= instance.getSteps().size()) {
            completeWorkflow(instance);
            return;
        }

        WorkflowStep currentStep = instance.getSteps().get(instance.getCurrentStepIndex());

        log.info("工作流步骤执行: workflow={}, step={}, target={}",
                instance.getInstanceId(), currentStep.getStepId(), currentStep.getTargetAgent());

        // 记录步骤开始
        instance.getHistory().add(new StepHistory(
                currentStep.getStepId(), StepStatus.EXECUTING, LocalDateTime.now(), null));

        // 发布任务委派事件给目标Agent
        BaseEvent delegateEvent = new BaseEvent(DomainEvents.TASK_DELEGATED,
                "orchestrator", currentStep.getTargetAgent(),
                BaseEvent.EventPriority.HIGH) {};
        delegateEvent.withPayload("workflowId", instance.getInstanceId())
                .withPayload("stepId", currentStep.getStepId())
                .withPayload("stepName", currentStep.getStepName())
                .withPayload("params", instance.getParams());
        eventBus.publish(delegateEvent);
    }

    /**
     * 处理步骤完成事件
     */
    private void handleStepCompleted(BaseEvent event) {
        String workflowId = event.getPayload("workflowId");
        String stepId = event.getPayload("stepId");
        Boolean success = event.getPayload("success");

        WorkflowInstance instance = activeWorkflows.get(workflowId);
        if (instance == null) {
            log.warn("工作流不存在: {}", workflowId);
            return;
        }

        WorkflowStep currentStep = instance.getSteps().get(instance.getCurrentStepIndex());

        if (Boolean.TRUE.equals(success)) {
            // 步骤成功
            log.info("工作流步骤完成: workflow={}, step={}", workflowId, stepId);
            instance.getHistory().add(new StepHistory(
                    stepId, StepStatus.COMPLETED, null, LocalDateTime.now()));

            // 执行下一步
            instance.setCurrentStepIndex(instance.getCurrentStepIndex() + 1);
            executeCurrentStep(instance);
        } else {
            // 步骤失败
            String reason = event.getPayload("reason");
            log.error("工作流步骤失败: workflow={}, step={}, reason={}", workflowId, stepId, reason);
            instance.getHistory().add(new StepHistory(
                    stepId, StepStatus.FAILED, null, LocalDateTime.now()));

            // 工作流失败
            failWorkflow(instance, reason);
        }
    }

    /**
     * 完成工作流
     */
    private void completeWorkflow(WorkflowInstance instance) {
        instance.setStatus(WorkflowStatus.COMPLETED);
        instance.setCompletedAt(LocalDateTime.now());

        log.info("工作流完成: instanceId={}, template={}, 耗时={}秒",
                instance.getInstanceId(), instance.getTemplateName(),
                java.time.Duration.between(instance.getStartedAt(), instance.getCompletedAt()).getSeconds());

        BaseEvent event = new BaseEvent(DomainEvents.WORKFLOW_COMPLETED, "orchestrator") {};
        event.withPayload("workflowId", instance.getInstanceId())
                .withPayload("templateName", instance.getTemplateName());
        eventBus.publish(event);
    }

    /**
     * 工作流失败
     */
    private void failWorkflow(WorkflowInstance instance, String reason) {
        instance.setStatus(WorkflowStatus.FAILED);
        instance.setCompletedAt(LocalDateTime.now());

        log.error("工作流失败: instanceId={}, reason={}", instance.getInstanceId(), reason);

        BaseEvent event = new BaseEvent(DomainEvents.WORKFLOW_FAILED,
                "orchestrator", null, BaseEvent.EventPriority.HIGH) {};
        event.withPayload("workflowId", instance.getInstanceId())
                .withPayload("reason", reason);
        eventBus.publish(event);
    }

    // ==================== 事件触发的工作流 ====================

    private void handleNewOrder(BaseEvent event) {
        String orderId = event.getPayload("orderId");
        log.info("触发新订单处理工作流: orderId={}", orderId);
        Map<String, Object> params = new HashMap<>();
        params.put("orderId", orderId);
        params.put("userId", event.getPayload("userId"));
        params.put("amount", event.getPayload("amount"));
        startWorkflow("new-order-processing", params);
    }

    private void handleRefundRequest(BaseEvent event) {
        String orderId = event.getPayload("orderId");
        log.info("触发退款处理工作流: orderId={}", orderId);
        startWorkflow("refund-processing", new HashMap<>(event.getPayload()));
    }

    private void handleChurnRisk(BaseEvent event) {
        String userId = event.getPayload("userId");
        log.info("触发用户流失挽回工作流: userId={}", userId);
        startWorkflow("churn-recovery", new HashMap<>(event.getPayload()));
    }

    private void handleStockDepleted(BaseEvent event) {
        String productId = event.getPayload("productId");
        log.info("触发库存补货工作流: productId={}", productId);
        startWorkflow("stock-replenishment", new HashMap<>(event.getPayload()));
    }

    /**
     * 获取所有活跃工作流
     */
    public Map<String, WorkflowInstance> getActiveWorkflows() {
        return Collections.unmodifiableMap(activeWorkflows);
    }

    /**
     * 获取工作流模板列表
     */
    public Map<String, WorkflowDefinition> getWorkflowTemplates() {
        return Collections.unmodifiableMap(workflowTemplates);
    }

    /**
     * 获取工作流详情
     */
    public WorkflowInstance getWorkflow(String instanceId) {
        return activeWorkflows.get(instanceId);
    }

    // ==================== 内部数据结构 ====================

    @Data
    public static class WorkflowDefinition {
        private final String templateId;
        private final String name;
        private final List<WorkflowStep> steps;
    }

    @Data
    public static class WorkflowStep {
        private final String stepId;
        private final String targetAgent;
        private final String stepName;
    }

    @Data
    public static class WorkflowInstance {
        private String instanceId;
        private String templateId;
        private String templateName;
        private List<WorkflowStep> steps;
        private int currentStepIndex;
        private WorkflowStatus status;
        private Map<String, Object> params;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private List<StepHistory> history;
    }

    @Data
    @lombok.AllArgsConstructor
    public static class StepHistory {
        private String stepId;
        private StepStatus status;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
    }

    public enum WorkflowStatus {
        RUNNING, COMPLETED, FAILED, CANCELLED
    }

    public enum StepStatus {
        PENDING, EXECUTING, COMPLETED, FAILED, SKIPPED
    }
}
