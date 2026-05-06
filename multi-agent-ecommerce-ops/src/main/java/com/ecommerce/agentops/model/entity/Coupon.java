package com.ecommerce.agentops.model.entity;

import com.ecommerce.agentops.model.enums.CouponType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券实体
 */
@Data
@Builder
public class Coupon {
    private String couponId;
    private String userId;
    private CouponType type;
    private BigDecimal discountAmount;
    private BigDecimal minSpend;        // 最低消费门槛
    private String applicableCategory;  // 适用分类(null=全品类)
    private boolean used;
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
    private String source;              // 来源: SYSTEM / AGENT / CAMPAIGN
}
