package com.ecommerce.agentops.service;

import com.ecommerce.agentops.event.BaseEvent;
import com.ecommerce.agentops.event.DomainEvents;
import com.ecommerce.agentops.event.EventBus;
import com.ecommerce.agentops.model.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户服务 - 用户管理和流失风险分析
 */
@Slf4j
@Service
public class UserService {

    private final Map<String, User> userStore = new ConcurrentHashMap<>();
    private final EventBus eventBus;

    public UserService(EventBus eventBus) {
        this.eventBus = eventBus;
        initSampleUsers();
    }

    private void initSampleUsers() {
        userStore.put("U001", User.builder()
                .userId("U001").username("张三").phone("138****1234")
                .email("zhangsan@example.com").level(3)
                .totalSpent(new BigDecimal("15680")).orderCount(12)
                .lastOrderTime(LocalDateTime.now().minusDays(5))
                .registeredAt(LocalDateTime.now().minusMonths(8))
                .churnRisk(false).build());

        userStore.put("U002", User.builder()
                .userId("U002").username("李四").phone("139****5678")
                .email("lisi@example.com").level(2)
                .totalSpent(new BigDecimal("3200")).orderCount(4)
                .lastOrderTime(LocalDateTime.now().minusDays(45))
                .registeredAt(LocalDateTime.now().minusMonths(6))
                .churnRisk(true).build());

        userStore.put("U003", User.builder()
                .userId("U003").username("王五").phone("137****9012")
                .email("wangwu@example.com").level(5)
                .totalSpent(new BigDecimal("89500")).orderCount(56)
                .lastOrderTime(LocalDateTime.now().minusDays(2))
                .registeredAt(LocalDateTime.now().minusYears(2))
                .churnRisk(false).build());

        userStore.put("U004", User.builder()
                .userId("U004").username("赵六").phone("136****3456")
                .email("zhaoliu@example.com").level(1)
                .totalSpent(new BigDecimal("299")).orderCount(1)
                .lastOrderTime(LocalDateTime.now().minusDays(90))
                .registeredAt(LocalDateTime.now().minusMonths(4))
                .churnRisk(true).build());
    }

    /**
     * 定时流失风险扫描 - 每30分钟扫描一次
     */
    @Scheduled(fixedRate = 1800000)
    public void scanChurnRisk() {
        log.info("开始流失风险扫描...");

        for (User user : userStore.values()) {
            if (user.getLastOrderTime() == null) continue;

            long daysSinceLastOrder = ChronoUnit.DAYS.between(
                    user.getLastOrderTime(), LocalDateTime.now());

            // 超过30天未下单视为流失风险
            boolean isChurnRisk = daysSinceLastOrder > 30;

            if (isChurnRisk && !user.isChurnRisk()) {
                user.setChurnRisk(true);
                int riskLevel = daysSinceLastOrder > 60 ? 9 : 6;

                // 发布流失风险事件
                BaseEvent event = new BaseEvent(DomainEvents.CHURN_RISK_DETECTED, "user-service") {};
                event.withPayload("userId", user.getUserId())
                        .withPayload("userName", user.getUsername())
                        .withPayload("daysSinceLastOrder", daysSinceLastOrder)
                        .withPayload("riskLevel", riskLevel)
                        .withPayload("totalSpent", user.getTotalSpent())
                        .withPayload("level", user.getLevel());
                eventBus.publish(event);

                log.warn("流失风险用户: userId={}, userName={}, {}天未下单, riskLevel={}",
                        user.getUserId(), user.getUsername(), daysSinceLastOrder, riskLevel);
            }
        }

        log.info("流失风险扫描完成");
    }

    /**
     * 获取用户信息
     */
    public User getUser(String userId) {
        return userStore.get(userId);
    }

    /**
     * 获取所有用户
     */
    public List<User> getAllUsers() {
        return new ArrayList<>(userStore.values());
    }

    /**
     * 获取流失风险用户
     */
    public List<User> getChurnRiskUsers() {
        return userStore.values().stream()
                .filter(User::isChurnRisk)
                .toList();
    }
}
