package com.ecommerce.agentops.service;

import com.ecommerce.agentops.event.BaseEvent;
import com.ecommerce.agentops.event.DomainEvents;
import com.ecommerce.agentops.event.EventBus;
import com.ecommerce.agentops.model.entity.CustomerSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客服会话服务 - 管理客户与Agent的交互会话
 */
@Slf4j
@Service
public class CustomerSessionService {

    private final Map<String, CustomerSession> sessionStore = new ConcurrentHashMap<>();
    private final EventBus eventBus;

    public CustomerSessionService(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * 创建客服会话并发送消息
     */
    public CustomerSession sendMessage(String userId, String message) {
        // 获取或创建会话
        String sessionId = sessionStore.entrySet().stream()
                .filter(e -> e.getValue().getUserId().equals(userId)
                        && e.getValue().getStatus() == CustomerSession.SessionStatus.ACTIVE)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseGet(() -> createSession(userId));

        CustomerSession session = sessionStore.get(sessionId);

        // 记录消息
        session.getMessages().add(CustomerSession.ChatMessage.builder()
                .role("CUSTOMER")
                .content(message)
                .timestamp(LocalDateTime.now())
                .build());

        // 发布客户咨询事件
        BaseEvent event = new BaseEvent(DomainEvents.CUSTOMER_INQUIRY, "session-service") {};
        event.withPayload("sessionId", sessionId)
                .withPayload("userId", userId)
                .withPayload("message", message);
        eventBus.publish(event);

        log.info("客户消息已发送: userId={}, sessionId={}", userId, sessionId);
        return session;
    }

    /**
     * 创建新会话
     */
    private String createSession(String userId) {
        String sessionId = "SESSION-" + userId + "-" + System.currentTimeMillis();
        CustomerSession session = CustomerSession.builder()
                .sessionId(sessionId)
                .userId(userId)
                .status(CustomerSession.SessionStatus.ACTIVE)
                .sentiment(CustomerSession.CustomerSentiment.NEUTRAL)
                .messages(new ArrayList<>())
                .startedAt(LocalDateTime.now())
                .build();
        sessionStore.put(sessionId, session);
        return sessionId;
    }

    /**
     * 获取会话
     */
    public CustomerSession getSession(String sessionId) {
        return sessionStore.get(sessionId);
    }

    /**
     * 获取用户的所有会话
     */
    public List<CustomerSession> getUserSessions(String userId) {
        return sessionStore.values().stream()
                .filter(s -> s.getUserId().equals(userId))
                .toList();
    }

    /**
     * 获取所有活跃会话
     */
    public List<CustomerSession> getActiveSessions() {
        return sessionStore.values().stream()
                .filter(s -> s.getStatus() == CustomerSession.SessionStatus.ACTIVE)
                .toList();
    }
}
