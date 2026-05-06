package com.ecommerce.agentops.model.entity;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品实体
 */
@Data
@Builder
public class Product {
    private String productId;
    private String name;
    private String category;
    private BigDecimal price;
    private BigDecimal costPrice;
    private int stock;
    private int warningStock;
    private boolean active;
    private int salesCount;
    private double rating;
}
