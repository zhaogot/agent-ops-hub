package com.ecommerce.agentops.controller;

import com.ecommerce.agentops.agent.marketing.MarketingAgent;
import com.ecommerce.agentops.model.entity.Coupon;
import com.ecommerce.agentops.service.UserService;
import com.ecommerce.agentops.model.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 营销API
 */
@RestController
@RequestMapping("/api/marketing")
@RequiredArgsConstructor
public class MarketingController {

    private final MarketingAgent marketingAgent;
    private final UserService userService;

    /**
     * 获取用户优惠券
     */
    @GetMapping("/coupons/{userId}")
    public ResponseEntity<List<Coupon>> getUserCoupons(@PathVariable String userId) {
        return ResponseEntity.ok(marketingAgent.getUserCoupons(userId));
    }

    /**
     * 获取活跃营销活动
     */
    @GetMapping("/campaigns")
    public ResponseEntity<Map<String, Map<String, Object>>> getActiveCampaigns() {
        return ResponseEntity.ok(marketingAgent.getActiveCampaigns());
    }

    /**
     * 获取流失风险用户
     */
    @GetMapping("/churn-risk")
    public ResponseEntity<List<User>> getChurnRiskUsers() {
        return ResponseEntity.ok(userService.getChurnRiskUsers());
    }
}
