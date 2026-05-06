package com.ecommerce.agentops.event;

import lombok.Getter;

/**
 * 电商运营领域事件定义
 *
 * 事件类型采用"领域.动作"命名法，清晰表达业务语义
 * 所有Agent通过事件类型进行匹配订阅，无需知道具体实现
 */
public final class DomainEvents {

    private DomainEvents() {}

    // ==================== 客服相关事件 ====================

    /** 客户发起咨询 */
    public static final String CUSTOMER_INQUIRY = "customer.inquiry";
    /** 客户投诉 */
    public static final String CUSTOMER_COMPLAINT = "customer.complaint";
    /** 客户情绪升级（从正常变为愤怒等） */
    public static final String CUSTOMER_EMOTION_ESCALATION = "customer.emotion.escalation";
    /** 客服回复消息 */
    public static final String CUSTOMER_SERVICE_REPLY = "customer.service.reply";
    /** 需要人工介入 */
    public static final String CUSTOMER_SERVICE_ESCALATION = "customer.service.escalation";

    // ==================== 订单相关事件 ====================

    /** 新订单创建 */
    public static final String ORDER_CREATED = "order.created";
    /** 订单支付成功 */
    public static final String ORDER_PAID = "order.paid";
    /** 订单发货 */
    public static final String ORDER_SHIPPED = "order.shipped";
    /** 订单签收 */
    public static final String ORDER_DELIVERED = "order.delivered";
    /** 申请退款 */
    public static final String ORDER_REFUND_REQUEST = "order.refund.request";
    /** 退款完成 */
    public static final String ORDER_REFUND_COMPLETED = "order.refund.completed";
    /** 订单异常 */
    public static final String ORDER_ANOMALY = "order.anomaly";

    // ==================== 营销相关事件 ====================

    /** 发放优惠券 */
    public static final String COUPON_ISSUED = "marketing.coupon.issued";
    /** 优惠券使用 */
    public static final String COUPON_USED = "marketing.coupon.used";
    /** 营销活动启动 */
    public static final String CAMPAIGN_STARTED = "marketing.campaign.started";
    /** 营销活动结束 */
    public static final String CAMPAIGN_ENDED = "marketing.campaign.ended";
    /** 用户流失风险检测 */
    public static final String CHURN_RISK_DETECTED = "marketing.churn.risk";
    /** 复购推荐触发 */
    public static final String REPURCHASE_RECOMMENDATION = "marketing.repurchase.recommendation";
    /** 价格变动通知 */
    public static final String PRICE_CHANGE = "marketing.price.change";

    // ==================== 运营相关事件 ====================

    /** 库存预警 */
    public static final String STOCK_WARNING = "operations.stock.warning";
    /** 库存耗尽 */
    public static final String STOCK_DEPLETED = "operations.stock.depleted";
    /** 自动补货触发 */
    public static final String AUTO_REORDER_TRIGGERED = "operations.reorder.triggered";
    /** 商品上架 */
    public static final String PRODUCT_LISTED = "operations.product.listed";
    /** 商品下架 */
    public static final String PRODUCT_DELISTED = "operations.product.delisted";
    /** 价格调整 */
    public static final String PRICE_ADJUSTED = "operations.price.adjusted";

    // ==================== 监控相关事件 ====================

    /** 系统健康检查 */
    public static final String HEALTH_CHECK = "monitoring.health.check";
    /** 异常检测 */
    public static final String ANOMALY_DETECTED = "monitoring.anomaly.detected";
    /** 性能告警 */
    public static final String PERFORMANCE_ALERT = "monitoring.performance.alert";
    /** 业务指标异常 */
    public static final String BUSINESS_METRIC_ALERT = "monitoring.metric.alert";
    /** Agent状态变更 */
    public static final String AGENT_STATUS_CHANGED = "monitoring.agent.status";

    // ==================== 编排相关事件 ====================

    /** 工作流启动 */
    public static final String WORKFLOW_STARTED = "orchestrator.workflow.started";
    /** 工作流步骤完成 */
    public static final String WORKFLOW_STEP_COMPLETED = "orchestrator.workflow.step.completed";
    /** 工作流完成 */
    public static final String WORKFLOW_COMPLETED = "orchestrator.workflow.completed";
    /** 工作流失败 */
    public static final String WORKFLOW_FAILED = "orchestrator.workflow.failed";
    /** 任务委派 */
    public static final String TASK_DELEGATED = "orchestrator.task.delegated";
}
