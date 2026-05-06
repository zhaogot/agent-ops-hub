package com.ecommerce.agentops.model.enums;

import lombok.Getter;

@Getter
public enum OrderStatus {
    PENDING("待支付"),
    PAID("已支付"),
    PROCESSING("处理中"),
    SHIPPED("已发货"),
    DELIVERED("已签收"),
    COMPLETED("已完成"),
    CANCELLED("已取消"),
    REFUND_REQUESTED("退款申请中"),
    REFUNDING("退款处理中"),
    REFUNDED("已退款");

    private final String displayName;

    OrderStatus(String displayName) {
        this.displayName = displayName;
    }
}
