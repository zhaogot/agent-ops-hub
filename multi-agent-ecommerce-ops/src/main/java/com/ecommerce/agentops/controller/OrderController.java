package com.ecommerce.agentops.controller;

import com.ecommerce.agentops.model.entity.Order;
import com.ecommerce.agentops.service.OrderService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 订单API
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * 创建订单 - 触发完整的订单处理工作流
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createOrder(@RequestBody CreateOrderRequest request) {
        List<Order.OrderItem> items = List.of(
                Order.OrderItem.builder()
                        .productId(request.getProductId())
                        .productName(request.getProductName())
                        .quantity(request.getQuantity())
                        .unitPrice(request.getPrice())
                        .subtotal(request.getPrice().multiply(BigDecimal.valueOf(request.getQuantity())))
                        .build()
        );

        Order order = orderService.createOrder(
                request.getUserId(), request.getUserName(), items,
                request.getShippingAddress(), request.getCouponId());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "订单创建成功",
                "orderId", order.getOrderId(),
                "totalAmount", order.getTotalAmount()
        ));
    }

    /**
     * 查询订单
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<Order> getOrder(@PathVariable String orderId) {
        Order order = orderService.getOrder(orderId);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(order);
    }

    /**
     * 查询用户订单列表
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Order>> getUserOrders(@PathVariable String userId) {
        return ResponseEntity.ok(orderService.getUserOrders(userId));
    }

    /**
     * 支付订单 - 触发支付处理流程
     */
    @PostMapping("/{orderId}/pay")
    public ResponseEntity<Map<String, Object>> payOrder(@PathVariable String orderId) {
        try {
            Order order = orderService.payOrder(orderId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "支付成功",
                    "orderId", order.getOrderId(),
                    "status", order.getStatus().getDisplayName()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * 申请退款 - 触发退款处理工作流
     */
    @PostMapping("/{orderId}/refund")
    public ResponseEntity<Map<String, Object>> requestRefund(
            @PathVariable String orderId, @RequestBody RefundRequest request) {
        try {
            Order order = orderService.requestRefund(orderId, request.getReason());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "退款申请已提交",
                    "orderId", order.getOrderId(),
                    "status", order.getStatus().getDisplayName()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    @Data
    public static class CreateOrderRequest {
        private String userId;
        private String userName;
        private String productId;
        private String productName;
        private int quantity;
        private BigDecimal price;
        private String shippingAddress;
        private String couponId;
    }

    @Data
    public static class RefundRequest {
        private String reason;
    }
}
