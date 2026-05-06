package com.ecommerce.agentops.model.entity;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户实体
 */
@Data
@Builder
public class User {
    private String userId;
    private String username;
    private String phone;
    private String email;
    private int level;             // 会员等级 1-5
    private BigDecimal totalSpent; // 累计消费
    private int orderCount;        // 订单数
    private LocalDateTime lastOrderTime;
    private LocalDateTime registeredAt;
    private boolean churnRisk;     // 流失风险标记
}
