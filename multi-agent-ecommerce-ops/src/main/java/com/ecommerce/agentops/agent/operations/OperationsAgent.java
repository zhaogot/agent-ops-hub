package com.ecommerce.agentops.agent.operations;

import com.ecommerce.agentops.agent.core.BaseAgent;
import com.ecommerce.agentops.event.BaseEvent;
import com.ecommerce.agentops.event.DomainEvents;
import com.ecommerce.agentops.event.EventBus;
import com.ecommerce.agentops.model.entity.Product;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 运营Agent - 日常运营自动化
 *
 * 核心职责:
 * 1. 库存管理: 监控库存，自动触发补货
 * 2. 价格管理: 监控竞品价格，动态调价
 * 3. 商品管理: 上下架管理，信息维护
 * 4. 数据统计: 收集运营数据，生成报表
 * 5. 异常处理: 处理运营层面的异常情况
 */
@Slf4j
@Component
public class OperationsAgent extends BaseAgent {

    /** 库存监控数据(内存缓存) */
    private final Map<String, Product> productCatalog = new ConcurrentHashMap<>();

    /** 价格历史记录 */
    private final Map<String, List<PriceRecord>> priceHistory = new ConcurrentHashMap<>();

    /** 库存预警阈值 */
    private static final int STOCK_WARNING_THRESHOLD = 50;
    private static final int STOCK_CRITICAL_THRESHOLD = 10;

    /** 价格波动阈值(百分比) */
    private static final double PRICE_FLUCTUATION_THRESHOLD = 10.0;

    public OperationsAgent(EventBus eventBus) {
        super(eventBus);
        initSampleProducts();
    }

    /**
     * 初始化示例商品数据
     */
    private void initSampleProducts() {
        productCatalog.put("P001", Product.builder()
                .productId("P001").name("智能手表Pro").category("数码")
                .price(new BigDecimal("999")).costPrice(new BigDecimal("600"))
                .stock(200).warningStock(50).active(true).salesCount(1500).rating(4.8).build());
        productCatalog.put("P002", Product.builder()
                .productId("P002").name("无线耳机X1").category("数码")
                .price(new BigDecimal("299")).costPrice(new BigDecimal("150"))
                .stock(8).warningStock(50).active(true).salesCount(3200).rating(4.6).build());
        productCatalog.put("P003", Product.builder()
                .productId("P003").name("运动背包").category("运动")
                .price(new BigDecimal("199")).costPrice(new BigDecimal("80"))
                .stock(500).warningStock(100).active(true).salesCount(800).rating(4.5).build());
        productCatalog.put("P004", Product.builder()
                .productId("P004").name("蓝牙音箱Mini").category("数码")
                .price(new BigDecimal("159")).costPrice(new BigDecimal("70"))
                .stock(3).warningStock(20).active(true).salesCount(2100).rating(4.3).build());
    }

    @Override
    public String getAgentId() {
        return "operations-agent";
    }

    @Override
    public String getAgentName() {
        return "智能运营Agent";
    }

    @Override
    public AgentType getAgentType() {
        return AgentType.OPERATIONS;
    }

    @Override
    public List<String> getSubscribedEventTypes() {
        return List.of(
                DomainEvents.ORDER_PAID,
                DomainEvents.ORDER_REFUND_COMPLETED,
                DomainEvents.STOCK_WARNING,
                DomainEvents.STOCK_DEPLETED,
                DomainEvents.AUTO_REORDER_TRIGGERED,
                DomainEvents.ORDER_ANOMALY,
                DomainEvents.PRODUCT_LISTED,
                DomainEvents.PRODUCT_DELISTED,
                DomainEvents.PRICE_ADJUSTED
        );
    }

    @Override
    protected void handleEvent(BaseEvent event) {
        switch (event.getEventType()) {
            case DomainEvents.ORDER_PAID -> handleOrderPaid(event);
            case DomainEvents.ORDER_REFUND_COMPLETED -> handleRefundCompleted(event);
            case DomainEvents.STOCK_WARNING -> handleStockWarning(event);
            case DomainEvents.STOCK_DEPLETED -> handleStockDepleted(event);
            case DomainEvents.AUTO_REORDER_TRIGGERED -> handleAutoReorder(event);
            case DomainEvents.ORDER_ANOMALY -> handleOrderAnomaly(event);
            case DomainEvents.PRODUCT_LISTED -> handleProductListed(event);
            case DomainEvents.PRODUCT_DELISTED -> handleProductDelisted(event);
            case DomainEvents.PRICE_ADJUSTED -> handlePriceAdjusted(event);
            default -> log.warn("未处理的事件类型: {}", event.getEventType());
        }
    }

    /**
     * 订单支付 - 扣减库存并检查库存水位
     */
    private void handleOrderPaid(BaseEvent event) {
        String orderId = event.getPayload("orderId");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = event.getPayload("items");

        if (items == null) return;

        log.info("订单支付处理库存: orderId={}", orderId);

        for (Map<String, Object> item : items) {
            String productId = (String) item.get("productId");
            int quantity = item.get("quantity") instanceof Number ?
                    ((Number) item.get("quantity")).intValue() : 1;

            Product product = productCatalog.get(productId);
            if (product != null) {
                product.setStock(product.getStock() - quantity);
                product.setSalesCount(product.getSalesCount() + quantity);
                log.info("库存扣减: productId={}, quantity={}, remaining={}",
                        productId, quantity, product.getStock());

                // 检查库存水位
                checkStockLevel(product);
            }
        }
    }

    /**
     * 退款完成 - 恢复库存
     */
    private void handleRefundCompleted(BaseEvent event) {
        String orderId = event.getPayload("orderId");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = event.getPayload("items");

        if (items == null) return;

        log.info("退款恢复库存: orderId={}", orderId);

        for (Map<String, Object> item : items) {
            String productId = (String) item.get("productId");
            int quantity = item.get("quantity") instanceof Number ?
                    ((Number) item.get("quantity")).intValue() : 1;

            Product product = productCatalog.get(productId);
            if (product != null) {
                product.setStock(product.getStock() + quantity);
                log.info("库存恢复: productId={}, quantity={}, total={}",
                        productId, quantity, product.getStock());
            }
        }
    }

    /**
     * 库存预警处理
     */
    private void handleStockWarning(BaseEvent event) {
        String productId = event.getPayload("productId");
        Integer currentStock = event.getPayload("currentStock");

        log.warn("库存预警: productId={}, currentStock={}", productId, currentStock);

        // 通知监控Agent
        BaseEvent alertEvent = createEvent(DomainEvents.BUSINESS_METRIC_ALERT,
                "monitoring-agent", BaseEvent.EventPriority.HIGH);
        alertEvent.withPayload("metricType", "STOCK_LOW")
                .withPayload("productId", productId)
                .withPayload("currentStock", currentStock)
                .withPayload("threshold", STOCK_WARNING_THRESHOLD);
        context.publishEvent(alertEvent);
    }

    /**
     * 库存耗尽处理 - 紧急下架并触发补货
     */
    private void handleStockDepleted(BaseEvent event) {
        String productId = event.getPayload("productId");

        Product product = productCatalog.get(productId);
        if (product != null) {
            product.setActive(false);
            log.error("商品库存耗尽，已自动下架: productId={}, name={}", productId, product.getName());

            // 触发自动补货
            BaseEvent reorderEvent = createEvent(DomainEvents.AUTO_REORDER_TRIGGERED,
                    null, BaseEvent.EventPriority.HIGH);
            reorderEvent.withPayload("productId", productId)
                    .withPayload("productName", product.getName())
                    .withPayload("reorderQuantity", 200);
            context.publishEvent(reorderEvent);

            // 发布下架事件
            BaseEvent delistEvent = createEvent(DomainEvents.PRODUCT_DELISTED);
            delistEvent.withPayload("productId", productId)
                    .withPayload("reason", "STOCK_DEPLETED");
            context.publishEvent(delistEvent);
        }
    }

    /**
     * 自动补货处理
     */
    private void handleAutoReorder(BaseEvent event) {
        String productId = event.getPayload("productId");
        Integer quantity = event.getPayload("reorderQuantity");

        log.info("自动补货触发: productId={}, quantity={}", productId, quantity);

        // 模拟补货成功
        Product product = productCatalog.get(productId);
        if (product != null) {
            int reorderQty = quantity != null ? quantity : 200;
            product.setStock(product.getStock() + reorderQty);
            product.setActive(true);
            log.info("补货完成: productId={}, newStock={}", productId, product.getStock());

            // 通知监控Agent补货完成
            BaseEvent alertEvent = createEvent(DomainEvents.BUSINESS_METRIC_ALERT,
                    "monitoring-agent", BaseEvent.EventPriority.NORMAL);
            alertEvent.withPayload("metricType", "REORDER_COMPLETED")
                    .withPayload("productId", productId)
                    .withPayload("quantity", reorderQty);
            context.publishEvent(alertEvent);
        }
    }

    /**
     * 订单异常处理
     */
    private void handleOrderAnomaly(BaseEvent event) {
        String orderId = event.getPayload("orderId");
        String anomalyType = event.getPayload("anomalyType");
        log.warn("订单异常处理: orderId={}, type={}", orderId, anomalyType);
        // 记录异常，通知监控Agent
    }

    private void handleProductListed(BaseEvent event) {
        String productId = event.getPayload("productId");
        log.info("商品上架: productId={}", productId);
    }

    private void handleProductDelisted(BaseEvent event) {
        String productId = event.getPayload("productId");
        log.info("商品下架: productId={}", productId);
    }

    /**
     * 价格调整处理
     */
    private void handlePriceAdjusted(BaseEvent event) {
        String productId = event.getPayload("productId");
        BigDecimal newPrice = event.getPayload("newPrice");

        Product product = productCatalog.get(productId);
        if (product != null) {
            BigDecimal oldPrice = product.getPrice();
            product.setPrice(newPrice);

            // 记录价格历史
            priceHistory.computeIfAbsent(productId, k -> new ArrayList<>())
                    .add(new PriceRecord(oldPrice, newPrice, LocalDateTime.now()));

            // 检查价格波动是否超过阈值
            double changePercent = newPrice.subtract(oldPrice)
                    .divide(oldPrice, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100")).abs().doubleValue();

            if (changePercent > PRICE_FLUCTUATION_THRESHOLD) {
                // 通知营销Agent
                BaseEvent priceChangeEvent = createEvent(DomainEvents.PRICE_CHANGE,
                        "marketing-agent", BaseEvent.EventPriority.NORMAL);
                priceChangeEvent.withPayload("productId", productId)
                        .withPayload("oldPrice", oldPrice)
                        .withPayload("newPrice", newPrice)
                        .withPayload("changePercent", changePercent);
                context.publishEvent(priceChangeEvent);
            }

            log.info("价格已调整: productId={}, {} -> {} ({}%)",
                    productId, oldPrice, newPrice,
                    String.format("%.1f", changePercent));
        }
    }

    /**
     * 检查库存水位
     */
    private void checkStockLevel(Product product) {
        if (product.getStock() <= STOCK_CRITICAL_THRESHOLD) {
            // 紧急库存耗尽
            BaseEvent event = createEvent(DomainEvents.STOCK_DEPLETED,
                    null, BaseEvent.EventPriority.CRITICAL);
            event.withPayload("productId", product.getProductId())
                    .withPayload("currentStock", product.getStock());
            context.publishEvent(event);
        } else if (product.getStock() <= STOCK_WARNING_THRESHOLD) {
            // 库存预警
            BaseEvent event = createEvent(DomainEvents.STOCK_WARNING,
                    null, BaseEvent.EventPriority.HIGH);
            event.withPayload("productId", product.getProductId())
                    .withPayload("productName", product.getName())
                    .withPayload("currentStock", product.getStock());
            context.publishEvent(event);
        }
    }

    /**
     * 获取商品目录
     */
    public Map<String, Product> getProductCatalog() {
        return Collections.unmodifiableMap(productCatalog);
    }

    /**
     * 获取价格历史
     */
    public List<PriceRecord> getPriceHistory(String productId) {
        return priceHistory.getOrDefault(productId, Collections.emptyList());
    }

    /**
     * 价格记录
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class PriceRecord {
        private BigDecimal oldPrice;
        private BigDecimal newPrice;
        private LocalDateTime changedAt;
    }
}
