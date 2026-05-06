package com.ecommerce.agentops.service;

import com.ecommerce.agentops.event.BaseEvent;
import com.ecommerce.agentops.event.DomainEvents;
import com.ecommerce.agentops.event.EventBus;
import com.ecommerce.agentops.model.entity.Order;
import com.ecommerce.agentops.model.enums.OrderStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 订单服务 - 管理订单生命周期
 */
@Slf4j
@Service
public class OrderService {

    private final Map<String, Order> orderStore = new ConcurrentHashMap<>();
    private final EventBus eventBus;

    public OrderService(EventBus eventBus) {
        this.eventBus = eventBus;
        initSampleOrders();
    }

    private void initSampleOrders() {
        orderStore.put("ORD-001", Order.builder()
                .orderId("ORD-001").userId("U001").userName("张三")
                .status(OrderStatus.PAID)
                .totalAmount(new BigDecimal("1298"))
                .actualAmount(new BigDecimal("1278"))
                .discountAmount(new BigDecimal("20"))
                .items(List.of(
                        Order.OrderItem.builder()
                                .productId("P001").productName("智能手表Pro")
                                .quantity(1).unitPrice(new BigDecimal("999")).subtotal(new BigDecimal("999")).build(),
                        Order.OrderItem.builder()
                                .productId("P004").productName("蓝牙音箱Mini")
                                .quantity(1).unitPrice(new BigDecimal("299")).subtotal(new BigDecimal("299")).build()
                ))
                .shippingAddress("北京市朝阳区xxx路xxx号")
                .createdAt(LocalDateTime.now().minusHours(2))
                .paidAt(LocalDateTime.now().minusHours(1))
                .build());

        orderStore.put("ORD-002", Order.builder()
                .orderId("ORD-002").userId("U002").userName("李四")
                .status(OrderStatus.SHIPPED)
                .totalAmount(new BigDecimal("299"))
                .actualAmount(new BigDecimal("299"))
                .items(List.of(
                        Order.OrderItem.builder()
                                .productId("P002").productName("无线耳机X1")
                                .quantity(1).unitPrice(new BigDecimal("299")).subtotal(new BigDecimal("299")).build()
                ))
                .shippingAddress("上海市浦东新区xxx路xxx号")
                .createdAt(LocalDateTime.now().minusDays(2))
                .paidAt(LocalDateTime.now().minusDays(2))
                .shippedAt(LocalDateTime.now().minusDays(1))
                .build());
    }

    /**
     * 创建订单
     */
    public Order createOrder(String userId, String userName, List<Order.OrderItem> items,
                              String shippingAddress, String couponId) {
        BigDecimal totalAmount = items.stream()
                .map(Order.OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Order order = Order.builder()
                .orderId(orderId)
                .userId(userId)
                .userName(userName)
                .status(OrderStatus.PENDING)
                .totalAmount(totalAmount)
                .actualAmount(totalAmount)
                .discountAmount(BigDecimal.ZERO)
                .items(items)
                .shippingAddress(shippingAddress)
                .couponId(couponId)
                .createdAt(LocalDateTime.now())
                .build();

        orderStore.put(orderId, order);
        log.info("订单创建: orderId={}, userId={}, amount={}", orderId, userId, totalAmount);

        // 发布订单创建事件
        BaseEvent event = new BaseEvent(DomainEvents.ORDER_CREATED, "order-service") {};
        event.withPayload("orderId", orderId)
                .withPayload("userId", userId)
                .withPayload("userName", userName)
                .withPayload("amount", totalAmount)
                .withPayload("itemCount", items.size());
        eventBus.publish(event);

        return order;
    }

    /**
     * 支付订单
     */
    public Order payOrder(String orderId) {
        Order order = orderStore.get(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在: " + orderId);
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new RuntimeException("订单状态不允许支付: " + order.getStatus());
        }

        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(LocalDateTime.now());

        // 发布支付成功事件
        BaseEvent event = new BaseEvent(DomainEvents.ORDER_PAID, "order-service") {};
        event.withPayload("orderId", orderId)
                .withPayload("userId", order.getUserId())
                .withPayload("amount", order.getActualAmount())
                .withPayload("items", order.getItems().stream()
                        .map(i -> Map.<String, Object>of("productId", i.getProductId(),
                                "quantity", i.getQuantity(), "productName", i.getProductName()))
                        .toList())
                .withPayload("category", "数码");
        eventBus.publish(event);

        log.info("订单支付成功: orderId={}", orderId);
        return order;
    }

    /**
     * 申请退款
     */
    public Order requestRefund(String orderId, String reason) {
        Order order = orderStore.get(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在: " + orderId);
        }

        order.setStatus(OrderStatus.REFUND_REQUESTED);

        BaseEvent event = new BaseEvent(DomainEvents.ORDER_REFUND_REQUEST, "order-service",
                null, BaseEvent.EventPriority.HIGH) {};
        event.withPayload("orderId", orderId)
                .withPayload("userId", order.getUserId())
                .withPayload("amount", order.getActualAmount())
                .withPayload("reason", reason)
                .withPayload("items", order.getItems());
        eventBus.publish(event);

        log.info("退款申请: orderId={}, reason={}", orderId, reason);
        return order;
    }

    /**
     * 查询订单
     */
    public Order getOrder(String orderId) {
        return orderStore.get(orderId);
    }

    /**
     * 查询用户订单列表
     */
    public List<Order> getUserOrders(String userId) {
        return orderStore.values().stream()
                .filter(o -> o.getUserId().equals(userId))
                .sorted(Comparator.comparing(Order::getCreatedAt).reversed())
                .toList();
    }

    /**
     * 获取所有订单
     */
    public List<Order> getAllOrders() {
        return new ArrayList<>(orderStore.values());
    }
}
