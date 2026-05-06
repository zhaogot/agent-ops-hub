package com.ecommerce.agentops.model.entity;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 客服会话实体 - 跟踪客户与客服Agent的交互过程
 */
@Data
@Builder
public class CustomerSession {
    private String sessionId;
    private String userId;
    private String orderId;  // 关联订单(可选)
    private SessionStatus status;
    private CustomerSentiment sentiment;
    private List<ChatMessage> messages;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private boolean needsHumanAgent;  // 是否需要人工客服

    @Data
    @Builder
    public static class ChatMessage {
        private String role;     // CUSTOMER / AGENT / SYSTEM
        private String content;
        private LocalDateTime timestamp;
    }

    public enum SessionStatus {
        ACTIVE, WAITING, RESOLVED, ESCALATED
    }

    public enum CustomerSentiment {
        POSITIVE, NEUTRAL, NEGATIVE, ANGRY
    }
}
