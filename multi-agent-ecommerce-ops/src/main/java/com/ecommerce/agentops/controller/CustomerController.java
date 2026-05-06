package com.ecommerce.agentops.controller;

import com.ecommerce.agentops.model.entity.CustomerSession;
import com.ecommerce.agentops.service.CustomerSessionService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 客服API
 */
@RestController
@RequestMapping("/api/customer")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerSessionService sessionService;

    /**
     * 发送客服消息 - 触发智能客服自动回复
     */
    @PostMapping("/message")
    public ResponseEntity<Map<String, Object>> sendMessage(@RequestBody MessageRequest request) {
        CustomerSession session = sessionService.sendMessage(request.getUserId(), request.getMessage());

        // 获取最新的Agent回复
        String lastReply = session.getMessages().stream()
                .filter(m -> "AGENT".equals(m.getRole()))
                .reduce((first, second) -> second)
                .map(CustomerSession.ChatMessage::getContent)
                .orElse("正在处理中...");

        return ResponseEntity.ok(Map.of(
                "success", true,
                "sessionId", session.getSessionId(),
                "reply", lastReply,
                "sentiment", session.getSentiment().name(),
                "needsHumanAgent", session.isNeedsHumanAgent()
        ));
    }

    /**
     * 获取会话历史
     */
    @GetMapping("/session/{sessionId}")
    public ResponseEntity<CustomerSession> getSession(@PathVariable String sessionId) {
        CustomerSession session = sessionService.getSession(sessionId);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(session);
    }

    /**
     * 获取用户所有会话
     */
    @GetMapping("/sessions/user/{userId}")
    public ResponseEntity<List<CustomerSession>> getUserSessions(@PathVariable String userId) {
        return ResponseEntity.ok(sessionService.getUserSessions(userId));
    }

    /**
     * 获取所有活跃会话
     */
    @GetMapping("/sessions/active")
    public ResponseEntity<List<CustomerSession>> getActiveSessions() {
        return ResponseEntity.ok(sessionService.getActiveSessions());
    }

    @Data
    public static class MessageRequest {
        private String userId;
        private String message;
    }
}
