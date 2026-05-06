package com.ecommerce.agentops.model.enums;

import lombok.Getter;

@Getter
public enum CouponType {
    FIXED("满减券"),
    PERCENT("折扣券"),
    FREE_SHIPPING("包邮券"),
    NEW_USER("新人券"),
    CASHBACK("返现券");

    private final String displayName;

    CouponType(String displayName) {
        this.displayName = displayName;
    }
}
