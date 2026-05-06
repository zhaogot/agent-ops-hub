package com.ecommerce.agentops.agent.marketing;

import com.ecommerce.agentops.agent.core.BaseAgent;
import com.ecommerce.agentops.event.BaseEvent;
import com.ecommerce.agentops.event.DomainEvents;
import com.ecommerce.agentops.event.EventBus;
import com.ecommerce.agentops.model.entity.Coupon;
import com.ecommerce.agentops.model.enums.CouponType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 营销Agent - 智能营销自动化
 *
 * 核心职责:
 * 1. 优惠券发放: 根据用户画像自动发放优惠券
 * 2. 流失预警: 识别流失风险用户，触发挽回策略
 * 3. 复购推荐: 基于购买历史推荐复购
 * 4. 营销活动: 管理和执行营销活动
 * 5. 价格策略: 配合运营Agent进行价格调整
 */
@Slf4j
@Component
public class MarketingAgent extends BaseAgent {

    /** 已发放的优惠券(内存存储，生产环境应持久化) */
    private final Map<String, List<Coupon>> userCoupons = new ConcurrentHashMap<>();

    /** 活跃的营销活动 */
    private final Map<String, Map<String, Object>> activeCampaigns = new ConcurrentHashMap<>();

    /** 用户行为数据缓存 */
    private final Map<String, Map<String, Object>> userBehaviorCache = new ConcurrentHashMap<>();

    public MarketingAgent(EventBus eventBus) {
        super(eventBus);
    }

    @Override
    public String getAgentId() {
        return "marketing-agent";
    }

    @Override
    public String getAgentName() {
        return "智能营销Agent";
    }

    @Override
    public AgentType getAgentType() {
        return AgentType.MARKETING;
    }

    @Override
    public List<String> getSubscribedEventTypes() {
        return List.of(
                DomainEvents.ORDER_CREATED,
                DomainEvents.ORDER_PAID,
                DomainEvents.COUPON_ISSUED,
                DomainEvents.CHURN_RISK_DETECTED,
                DomainEvents.CAMPAIGN_STARTED,
                DomainEvents.CAMPAIGN_ENDED,
                DomainEvents.PRICE_CHANGE,
                DomainEvents.CUSTOMER_EMOTION_ESCALATION
        );
    }

    @Override
    protected void handleEvent(BaseEvent event) {
        switch (event.getEventType()) {
            case DomainEvents.ORDER_CREATED -> handleOrderCreated(event);
            case DomainEvents.ORDER_PAID -> handleOrderPaid(event);
            case DomainEvents.COUPON_ISSUED -> handleCouponIssued(event);
            case DomainEvents.CHURN_RISK_DETECTED -> handleChurnRisk(event);
            case DomainEvents.CAMPAIGN_STARTED -> handleCampaignStarted(event);
            case DomainEvents.CAMPAIGN_ENDED -> handleCampaignEnded(event);
            case DomainEvents.PRICE_CHANGE -> handlePriceChange(event);
            case DomainEvents.CUSTOMER_EMOTION_ESCALATION -> handleEmotionEscalation(event);
            default -> log.warn("未处理的事件类型: {}", event.getEventType());
        }
    }

    /**
     * 新订单创建 - 分析用户行为，触发相关营销策略
     */
    private void handleOrderCreated(BaseEvent event) {
        String userId = event.getPayload("userId");
        BigDecimal amount = event.getPayload("amount");
        String orderId = event.getPayload("orderId");

        log.info("分析新订单的营销机会: userId={}, orderId={}, amount={}", userId, orderId, amount);

        // 更新用户行为缓存
        Map<String, Object> behavior = userBehaviorCache.computeIfAbsent(userId,
                k -> new ConcurrentHashMap<>());
        behavior.put("lastOrderTime", LocalDateTime.now());
        behavior.put("lastOrderAmount", amount);

        // 新用户首单 - 发放复购券
        Integer orderCount = event.getPayload("orderCount");
        if (orderCount != null && orderCount == 1) {
            issueCoupon(userId, CouponType.FIXED, new BigDecimal("10"),
                    new BigDecimal("50"), "新人复购券", "7天后可用");
        }
    }

    /**
     * 订单支付完成 - 更新用户画像，判断是否触发复购推荐
     */
    private void handleOrderPaid(BaseEvent event) {
        String userId = event.getPayload("userId");
        String category = event.getPayload("category");

        log.info("订单支付完成，分析复购机会: userId={}, category={}", userId, category);

        // 基于品类的复购推荐
        if (category != null) {
            Map<String, Object> behavior = userBehaviorCache.computeIfAbsent(userId,
                    k -> new ConcurrentHashMap<>());
            behavior.put("lastPurchaseCategory", category);
            behavior.put("lastPurchaseTime", LocalDateTime.now());

            // 发布复购推荐事件（延迟3天后的场景，此处简化为直接发布）
            BaseEvent recommendationEvent = createEvent(DomainEvents.REPURCHASE_RECOMMENDATION);
            recommendationEvent.withPayload("userId", userId)
                    .withPayload("category", category)
                    .withPayload("recommendationType", "CROSS_SELL");
            context.publishEvent(recommendationEvent);
        }
    }

    /**
     * 优惠券发放请求 - 处理来自其他Agent的优惠券发放
     */
    private void handleCouponIssued(BaseEvent event) {
        String userId = event.getPayload("userId");
        String reason = event.getPayload("reason");
        Integer discountAmount = event.getPayload("discountAmount");

        if (discountAmount == null) discountAmount = 10;

        log.info("处理优惠券发放: userId={}, reason={}, amount={}", userId, reason, discountAmount);

        Coupon coupon = Coupon.builder()
                .couponId(UUID.randomUUID().toString().substring(0, 8))
                .userId(userId)
                .type(CouponType.FIXED)
                .discountAmount(new BigDecimal(discountAmount))
                .minSpend(new BigDecimal(discountAmount * 3))
                .used(false)
                .issuedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(30))
                .source("AGENT_" + event.getSourceAgentId())
                .build();

        userCoupons.computeIfAbsent(userId, k -> new ArrayList<>()).add(coupon);
        log.info("优惠券已发放: couponId={}, userId={}, amount={}", coupon.getCouponId(), userId, discountAmount);
    }

    /**
     * 流失风险处理 - 发放挽回券并发送召回消息
     */
    private void handleChurnRisk(BaseEvent event) {
        String userId = event.getPayload("userId");
        Integer riskLevel = event.getPayload("riskLevel");

        log.warn("流失风险用户: userId={}, riskLevel={}", userId, riskLevel);

        // 根据风险等级发放不同额度的挽回券
        int discountAmount = riskLevel != null && riskLevel > 7 ? 30 : 20;
        issueCoupon(userId, CouponType.FIXED, new BigDecimal(discountAmount),
                new BigDecimal(discountAmount * 2), "用户挽回券", "流失风险挽回");

        // 发布召回通知
        BaseEvent notificationEvent = createEvent(DomainEvents.PRICE_CHANGE);
        notificationEvent.withPayload("userId", userId)
                .withPayload("type", "CHURN_RECALL")
                .withPayload("message", "我们想你了！专属" + discountAmount + "元优惠券已到账，快来逛逛吧~");
        context.publishEvent(notificationEvent);
    }

    /**
     * 营销活动启动
     */
    private void handleCampaignStarted(BaseEvent event) {
        String campaignId = event.getPayload("campaignId");
        String campaignName = event.getPayload("campaignName");

        log.info("营销活动已启动: id={}, name={}", campaignId, campaignName);
        activeCampaigns.put(campaignId, new HashMap<>(event.getPayload()));
    }

    /**
     * 营销活动结束
     */
    private void handleCampaignEnded(BaseEvent event) {
        String campaignId = event.getPayload("campaignId");
        activeCampaigns.remove(campaignId);
        log.info("营销活动已结束: id={}", campaignId);
    }

    /**
     * 价格变动通知
     */
    private void handlePriceChange(BaseEvent event) {
        String productId = event.getPayload("productId");
        log.info("收到价格变动通知: productId={}", productId);
        // 检查是否有用户对该商品有优惠券，触发提醒
    }

    /**
     * 客户情绪升级 - 配合客服Agent进行安抚营销
     */
    private void handleEmotionEscalation(BaseEvent event) {
        String userId = event.getPayload("userId");
        String sentiment = event.getPayload("sentiment");

        if ("ANGRY".equals(sentiment)) {
            // 愤怒用户发放高额度安抚券
            issueCoupon(userId, CouponType.FIXED, new BigDecimal("50"),
                    new BigDecimal("100"), "情绪安抚券", "客户投诉安抚");
            log.info("已为愤怒用户发放安抚券: userId={}", userId);
        }
    }

    /**
     * 发放优惠券的通用方法
     */
    private Coupon issueCoupon(String userId, CouponType type, BigDecimal discount,
                                BigDecimal minSpend, String category, String source) {
        Coupon coupon = Coupon.builder()
                .couponId(UUID.randomUUID().toString().substring(0, 8))
                .userId(userId)
                .type(type)
                .discountAmount(discount)
                .minSpend(minSpend)
                .used(false)
                .issuedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(30))
                .source(source)
                .build();

        userCoupons.computeIfAbsent(userId, k -> new ArrayList<>()).add(coupon);
        log.info("优惠券发放: couponId={}, userId={}, type={}, amount={}",
                coupon.getCouponId(), userId, type, discount);
        return coupon;
    }

    /**
     * 获取用户优惠券列表
     */
    public List<Coupon> getUserCoupons(String userId) {
        return userCoupons.getOrDefault(userId, Collections.emptyList());
    }

    /**
     * 获取活跃营销活动
     */
    public Map<String, Map<String, Object>> getActiveCampaigns() {
        return Collections.unmodifiableMap(activeCampaigns);
    }

    /**
     * 判断当前是否在营销时间窗口内（8:00-22:00）
     */
    private boolean isInCampaignHours() {
        LocalTime now = LocalTime.now();
        return now.isAfter(LocalTime.of(8, 0)) && now.isBefore(LocalTime.of(22, 0));
    }
}
