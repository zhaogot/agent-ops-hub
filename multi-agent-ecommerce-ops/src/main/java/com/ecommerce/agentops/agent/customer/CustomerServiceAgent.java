package com.ecommerce.agentops.agent.customer;

import com.ecommerce.agentops.agent.core.BaseAgent;
import com.ecommerce.agentops.agent.core.LlmReasoningService;
import com.ecommerce.agentops.agent.core.LlmReasoningService.CustomerServiceReasoning;
import com.ecommerce.agentops.event.BaseEvent;
import com.ecommerce.agentops.event.DomainEvents;
import com.ecommerce.agentops.event.EventBus;
import com.ecommerce.agentops.model.entity.CustomerSession;
import com.ecommerce.agentops.model.entity.CustomerSession.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客服Agent - 智能客服自动化
 *
 * 核心职责:
 * 1. 自动应答: 处理客户咨询，给出智能回复
 * 2. 情绪识别: 分析客户情绪，及时升级
 * 3. 订单查询: 处理订单相关查询和操作
 * 4. 退款处理: 自动处理退款申请（符合条件时）
 * 5. 人工转接: 识别需要人工介入的场景
 */
@Slf4j
@Component
public class CustomerServiceAgent extends BaseAgent {

    /** 活跃的客服会话 */
    private final Map<String, CustomerSession> activeSessions = new ConcurrentHashMap<>();

    /** 关键词 -> 回复模板映射 */
    private final Map<String, String> keywordResponses = new HashMap<>();

    /** 需要升级的关键词 */
    private final List<String> escalationKeywords = Arrays.asList(
            "投诉", "退款", "差评", "举报", "消协", "工商", "315", "律师", "起诉"
    );

    /** LLM推理服务 */
    private final LlmReasoningService llmReasoningService;

    public CustomerServiceAgent(EventBus eventBus, LlmReasoningService llmReasoningService) {
        super(eventBus);
        this.llmReasoningService = llmReasoningService;
        initResponseTemplates();
    }

    private void initResponseTemplates() {
        keywordResponses.put("发货", "您的订单正在加急处理中，预计24小时内发出，发货后会短信通知您哦~");
        keywordResponses.put("物流", "正在为您查询物流信息，请稍候...");
        keywordResponses.put("退款", "非常抱歉给您带来不便。请您提供订单号，我马上为您处理退款。");
        keywordResponses.put("尺寸", "请参考商品详情页的尺码表，如需进一步帮助可以提供您的身高体重，我来帮您推荐~");
        keywordResponses.put("质量", "非常抱歉遇到质量问题，请您拍照发给我，我们会第一时间为您处理。");
        keywordResponses.put("优惠", "我们正在进行限时优惠活动，新人首单立减20元，更多优惠可以查看活动专区~");
        keywordResponses.put("支付", "支持支付宝、微信、银行卡等多种支付方式，如有支付问题建议更换支付方式重试。");
        keywordResponses.put("发票", "已完成订单可申请电子发票，在订单详情页点击\"申请发票\"即可。");
    }

    @Override
    public String getAgentId() {
        return "customer-service-agent";
    }

    @Override
    public String getAgentName() {
        return "智能客服Agent";
    }

    @Override
    public AgentType getAgentType() {
        return AgentType.CUSTOMER_SERVICE;
    }

    @Override
    public List<String> getSubscribedEventTypes() {
        return List.of(
                DomainEvents.CUSTOMER_INQUIRY,
                DomainEvents.CUSTOMER_COMPLAINT,
                DomainEvents.ORDER_REFUND_REQUEST,
                DomainEvents.ORDER_ANOMALY,
                DomainEvents.CAMPAIGN_STARTED
        );
    }

    @Override
    protected void handleEvent(BaseEvent event) {
        switch (event.getEventType()) {
            case DomainEvents.CUSTOMER_INQUIRY -> handleInquiry(event);
            case DomainEvents.CUSTOMER_COMPLAINT -> handleComplaint(event);
            case DomainEvents.ORDER_REFUND_REQUEST -> handleRefundRequest(event);
            case DomainEvents.ORDER_ANOMALY -> handleOrderAnomaly(event);
            case DomainEvents.CAMPAIGN_STARTED -> handleCampaignNotification(event);
            default -> log.warn("未处理的事件类型: {}", event.getEventType());
        }
    }

    /**
     * 处理客户咨询 - LLM长链推理 + 规则引擎guardrail
     *
     * 推理链路: 意图识别 → 情绪分析 → 升级判断 → 回复生成 → 动作执行
     * LLM失败时自动降级到规则引擎
     */
    private void handleInquiry(BaseEvent event) {
        String sessionId = event.getPayload("sessionId");
        String userId = event.getPayload("userId");
        String message = event.getPayload("message");

        log.info("处理客户咨询: userId={}, message={}", userId, message);

        // 获取或创建会话
        CustomerSession session = activeSessions.computeIfAbsent(sessionId,
                id -> CustomerSession.builder()
                        .sessionId(id)
                        .userId(userId)
                        .status(CustomerSession.SessionStatus.ACTIVE)
                        .sentiment(CustomerSession.CustomerSentiment.NEUTRAL)
                        .messages(new ArrayList<>())
                        .startedAt(LocalDateTime.now())
                        .build());

        // 记录客户消息
        session.getMessages().add(ChatMessage.builder()
                .role("CUSTOMER")
                .content(message)
                .timestamp(LocalDateTime.now())
                .build());

        // ========== 尝试LLM长链推理 ==========
        CustomerSession.CustomerSentiment sentiment;
        String reply;
        boolean needsEscalation = false;
        List<Map<String, Object>> actions = null;

        try {
            Map<String, Object> contextInfo = Map.of(
                    "sessionId", sessionId,
                    "orderHistory", session.getOrderId() != null ? session.getOrderId() : "无",
                    "previousMessages", session.getMessages().size()
            );

            CustomerServiceReasoning reasoning = llmReasoningService.reasonCustomerService(
                    message, userId, contextInfo);

            if (reasoning != null) {
                // LLM推理成功 - 使用LLM结果
                sentiment = parseSentiment(reasoning.getSentiment());
                reply = reasoning.getReply();
                needsEscalation = reasoning.isNeedsEscalation();
                actions = reasoning.getActions();
                log.info("LLM推理成功: intent={}, sentiment={}, confidence={}",
                        reasoning.getIntent(), reasoning.getSentiment(), reasoning.getConfidence());
            } else {
                // LLM返回null - 降级到规则引擎
                log.info("LLM返回null，降级到规则引擎");
                sentiment = analyzeSentiment(message);
                needsEscalation = shouldEscalate(message, sentiment);
                reply = generateReply(message, session);
            }
        } catch (Exception e) {
            // LLM异常 - 降级到规则引擎 (guardrail)
            log.warn("LLM推理异常，降级到规则引擎: {}", e.getMessage());
            sentiment = analyzeSentiment(message);
            needsEscalation = shouldEscalate(message, sentiment);
            reply = generateReply(message, session);
        }

        // ========== Guardrail: 安全边界检查 ==========
        // 无论LLM还是规则引擎，都必须通过的安全检查
        if (sentiment == CustomerSession.CustomerSentiment.ANGRY && !needsEscalation) {
            // 安全兜底: 愤怒情绪必须升级（防止LLM漏判）
            needsEscalation = true;
            log.warn("Guardrail触发: 愤怒情绪未被标记升级，强制升级");
        }
        if (escalationKeywords.stream().anyMatch(message::contains) && !needsEscalation) {
            needsEscalation = true;
            log.warn("Guardrail触发: 升级关键词未被识别，强制升级");
        }

        session.setSentiment(sentiment);

        // ========== 执行决策 ==========
        if (needsEscalation) {
            escalateToHuman(session, message, sentiment);
            return;
        }

        // 执行LLM返回的动作（如发券）
        if (actions != null) {
            executeActions(actions, userId);
        }

        // 记录回复
        session.getMessages().add(ChatMessage.builder()
                .role("AGENT")
                .content(reply)
                .timestamp(LocalDateTime.now())
                .build());

        // 发布回复事件
        BaseEvent replyEvent = createEvent(DomainEvents.CUSTOMER_SERVICE_REPLY);
        replyEvent.withPayload("sessionId", sessionId)
                .withPayload("userId", userId)
                .withPayload("reply", reply)
                .withPayload("sentiment", sentiment.name());
        context.publishEvent(replyEvent);

        log.info("自动回复已发送: sessionId={}, reply={}", sessionId, reply);
    }

    /**
     * 处理客户投诉
     */
    private void handleComplaint(BaseEvent event) {
        String userId = event.getPayload("userId");
        String orderId = event.getPayload("orderId");
        String complaint = event.getPayload("complaint");

        log.warn("客户投诉: userId={}, orderId={}, content={}", userId, orderId, complaint);

        // 投诉直接触发情绪升级事件
        BaseEvent escalationEvent = createEvent(DomainEvents.CUSTOMER_EMOTION_ESCALATION,
                null, BaseEvent.EventPriority.HIGH);
        escalationEvent.withPayload("userId", userId)
                .withPayload("orderId", orderId)
                .withPayload("complaint", complaint)
                .withPayload("sentiment", "ANGRY")
                .withPayload("autoAction", "投诉已记录，准备转人工");
        context.publishEvent(escalationEvent);

        // 同时通知营销Agent发放安抚券
        BaseEvent couponEvent = createEvent(DomainEvents.COUPON_ISSUED,
                "marketing-agent", BaseEvent.EventPriority.HIGH);
        couponEvent.withPayload("userId", userId)
                .withPayload("reason", "COMPLAINT_COMPENSATION")
                .withPayload("discountAmount", 30);
        context.publishEvent(couponEvent);
    }

    /**
     * 处理退款申请
     */
    private void handleRefundRequest(BaseEvent event) {
        String orderId = event.getPayload("orderId");
        String userId = event.getPayload("userId");
        String reason = event.getPayload("reason");

        log.info("处理退款申请: orderId={}, userId={}, reason={}", orderId, userId, reason);

        // 简单退款自动审批（金额<100且非恶意退款）
        BigDecimal amount = event.getPayload("amount");
        if (amount != null && amount.compareTo(new java.math.BigDecimal("100")) < 0) {
            // 自动退款
            BaseEvent refundEvent = createEvent(DomainEvents.ORDER_REFUND_COMPLETED);
            refundEvent.withPayload("orderId", orderId)
                    .withPayload("userId", userId)
                    .withPayload("amount", amount)
                    .withPayload("autoApproved", true);
            context.publishEvent(refundEvent);
            log.info("小额退款自动审批通过: orderId={}", orderId);
        } else {
            // 需要人工审批
            BaseEvent escEvent = createEvent(DomainEvents.CUSTOMER_SERVICE_ESCALATION,
                    null, BaseEvent.EventPriority.HIGH);
            escEvent.withPayload("type", "REFUND_APPROVAL")
                    .withPayload("orderId", orderId)
                    .withPayload("userId", userId);
            context.publishEvent(escEvent);
        }
    }

    /**
     * 处理订单异常
     */
    private void handleOrderAnomaly(BaseEvent event) {
        String orderId = event.getPayload("orderId");
        String anomalyType = event.getPayload("anomalyType");
        log.warn("订单异常通知: orderId={}, anomalyType={}", orderId, anomalyType);
        // 通知运营Agent处理
        BaseEvent opsEvent = createEvent(DomainEvents.ORDER_ANOMALY,
                "operations-agent", BaseEvent.EventPriority.HIGH);
        opsEvent.withPayload("orderId", orderId)
                .withPayload("anomalyType", anomalyType)
                .withPayload("source", "customer-service");
        context.publishEvent(opsEvent);
    }

    /**
     * 营销活动通知 - 将活动信息推送给在线客户
     */
    private void handleCampaignNotification(BaseEvent event) {
        String campaignName = event.getPayload("campaignName");
        log.info("收到营销活动通知: {}", campaignName);
        // 在下次客户咨询时自动推荐活动
        activeSessions.values().forEach(session -> {
            if (session.getStatus() == CustomerSession.SessionStatus.ACTIVE) {
                session.getMessages().add(ChatMessage.builder()
                        .role("SYSTEM")
                        .content("[活动提醒] " + campaignName + " 正在进行中！")
                        .timestamp(LocalDateTime.now())
                        .build());
            }
        });
    }

    /**
     * 情绪分析 - 基于关键词的简单情绪识别
     */
    private CustomerSession.CustomerSentiment analyzeSentiment(String message) {
        String lower = message.toLowerCase();

        // 愤怒情绪关键词
        List<String> angryWords = List.of("生气", "愤怒", "太过分", "垃圾", "骗子", "投诉", "举报", "差评", "恶心");
        if (angryWords.stream().anyMatch(lower::contains)) {
            return CustomerSession.CustomerSentiment.ANGRY;
        }

        // 负面情绪关键词
        List<String> negativeWords = List.of("失望", "不满", "慢", "差", "坏", "破", "烂", "等很久", "不行");
        if (negativeWords.stream().anyMatch(lower::contains)) {
            return CustomerSession.CustomerSentiment.NEGATIVE;
        }

        // 正面情绪关键词
        List<String> positiveWords = List.of("满意", "好", "棒", "喜欢", "感谢", "谢谢", "不错", "推荐");
        if (positiveWords.stream().anyMatch(lower::contains)) {
            return CustomerSession.CustomerSentiment.POSITIVE;
        }

        return CustomerSession.CustomerSentiment.NEUTRAL;
    }

    /**
     * 判断是否需要升级到人工客服
     */
    private boolean shouldEscalate(String message, CustomerSession.CustomerSentiment sentiment) {
        // 愤怒情绪直接升级
        if (sentiment == CustomerSession.CustomerSentiment.ANGRY) {
            return true;
        }
        // 包含升级关键词
        return escalationKeywords.stream().anyMatch(message::contains);
    }

    /**
     * 升级到人工客服
     */
    private void escalateToHuman(CustomerSession session, String message,
                                  CustomerSession.CustomerSentiment sentiment) {
        session.setStatus(CustomerSession.SessionStatus.ESCALATED);
        session.setNeedsHumanAgent(true);

        BaseEvent event = createEvent(DomainEvents.CUSTOMER_SERVICE_ESCALATION,
                null, BaseEvent.EventPriority.CRITICAL);
        event.withPayload("sessionId", session.getSessionId())
                .withPayload("userId", session.getUserId())
                .withPayload("sentiment", sentiment.name())
                .withPayload("lastMessage", message)
                .withPayload("reason", "情绪升级/关键词触发");
        context.publishEvent(event);

        log.warn("会话升级至人工客服: sessionId={}, sentiment={}", session.getSessionId(), sentiment);
    }

    /**
     * 生成智能回复
     */
    private String generateReply(String message, CustomerSession session) {
        // 关键词匹配
        for (Map.Entry<String, String> entry : keywordResponses.entrySet()) {
            if (message.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // 基于会话历史的上下文回复
        if (session.getOrderId() != null) {
            return "关于您的订单 " + session.getOrderId() + "，正在为您查询中，请稍候...";
        }

        // 默认回复
        return "感谢您的咨询！请问还有什么可以帮到您的吗？如有订单问题请提供订单号，我会尽快为您处理~";
    }

    @Override
    protected void onStart() {
        log.info("客服Agent初始化完成，回复模板{}个，LLM推理已启用", keywordResponses.size());
    }

    /**
     * 将LLM返回的情绪字符串转换为枚举
     */
    private CustomerSession.CustomerSentiment parseSentiment(String sentiment) {
        if (sentiment == null) return CustomerSession.CustomerSentiment.NEUTRAL;
        try {
            return CustomerSession.CustomerSentiment.valueOf(sentiment.toUpperCase());
        } catch (IllegalArgumentException e) {
            return CustomerSession.CustomerSentiment.NEUTRAL;
        }
    }

    /**
     * 执行LLM返回的动作列表
     */
    private void executeActions(List<Map<String, Object>> actions, String userId) {
        for (Map<String, Object> action : actions) {
            String type = (String) action.get("type");
            if (type == null) continue;

            switch (type) {
                case "ISSUE_COUPON" -> {
                    Object amountObj = action.getOrDefault("params", action).toString().contains("amount")
                            ? ((Map<?, ?>) action.getOrDefault("params", action)).get("amount") : 10;
                    int amount = amountObj instanceof Number ? ((Number) amountObj).intValue() : 10;
                    BaseEvent couponEvent = createEvent(DomainEvents.COUPON_ISSUED,
                            "marketing-agent", BaseEvent.EventPriority.NORMAL);
                    couponEvent.withPayload("userId", userId)
                            .withPayload("reason", "LLM_RECOMMENDED")
                            .withPayload("discountAmount", amount);
                    context.publishEvent(couponEvent);
                    log.info("LLM推荐动作: 发放{}元优惠券给用户{}", amount, userId);
                }
                case "ESCALATE_TO_HUMAN" -> {
                    log.info("LLM推荐动作: 转人工客服");
                }
                default -> log.debug("未识别的LLM动作: {}", type);
            }
        }
    }

    @Override
    protected void onStop() {
        // 关闭所有活跃会话
        activeSessions.values().forEach(s -> {
            s.setStatus(CustomerSession.SessionStatus.RESOLVED);
            s.setEndedAt(LocalDateTime.now());
        });
        activeSessions.clear();
    }
}
