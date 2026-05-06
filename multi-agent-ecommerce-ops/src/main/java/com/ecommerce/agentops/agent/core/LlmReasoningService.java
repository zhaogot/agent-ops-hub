package com.ecommerce.agentops.agent.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * LLM推理服务 - 基于Spring AI的ReAct多步推理链
 *
 * ReAct模式: Reasoning(推理) + Acting(行动)
 * 每一步都包含: Thought(思考) → Action(行动) → Observation(观察) → 下一步思考
 *
 * 当前实现为单轮推理（一次LLM调用完成完整推理链），
 * 如需多轮工具调用，可扩展为循环ReAct模式。
 */
@Slf4j
@Service
public class LlmReasoningService {

    private final ChatClient customerServiceChatClient;
    private final ChatClient marketingChatClient;
    private final ChatClient operationsChatClient;
    private final ObjectMapper objectMapper;

    public LlmReasoningService(
            @Qualifier("customerServiceChatClient") ChatClient customerServiceChatClient,
            @Qualifier("marketingChatClient") ChatClient marketingChatClient,
            @Qualifier("operationsChatClient") ChatClient operationsChatClient,
            ObjectMapper objectMapper) {
        this.customerServiceChatClient = customerServiceChatClient;
        this.marketingChatClient = marketingChatClient;
        this.operationsChatClient = operationsChatClient;
        this.objectMapper = objectMapper;
    }

    // ==================== 客服Agent推理 ====================

    /**
     * 客服消息推理 - 完整ReAct链
     * 意图识别 → 情绪分析 → 决策 → 回复生成
     */
    public CustomerServiceReasoning reasonCustomerService(String message, String userId,
                                                            Map<String, Object> context) {
        long startTime = System.currentTimeMillis();

        String prompt = """
                ## 客户消息分析任务

                **客户消息:** %s
                **用户ID:** %s
                **上下文:** %s

                请按以下步骤进行推理分析，输出JSON格式：

                ```json
                {
                  "intent": "咨询/投诉/退款/查询/闲聊/其他",
                  "sentiment": "POSITIVE/NEUTRAL/NEGATIVE/ANGRY",
                  "confidence": 0.0-1.0,
                  "needsEscalation": true/false,
                  "escalationReason": "升级原因(如需)",
                  "reply": "回复内容",
                  "actions": [
                    {"type": "ISSUE_COUPON", "params": {"amount": 30, "reason": "..."}},
                    {"type": "ESCALATE_TO_HUMAN", "params": {"reason": "..."}}
                  ],
                  "reasoning": "推理过程说明"
                }
                ```

                判断规则：
                - 情绪为ANGRY或包含投诉/退款/差评/消协/工商/315 → needsEscalation=true
                - 投诉场景建议发放安抚券(ISSUE_COUPON)
                - 回复要专业、有同理心、提供具体方案
                """.formatted(message, userId, context);

        try {
            String response = customerServiceChatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            CustomerServiceReasoning result = parseJson(response, CustomerServiceReasoning.class);
            long duration = System.currentTimeMillis() - startTime;
            log.info("客服LLM推理完成: userId={}, intent={}, sentiment={}, needsEscalation={}, 耗时={}ms",
                    userId, result.getIntent(), result.getSentiment(), result.isNeedsEscalation(), duration);
            return result;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("客服LLM推理失败，降级到规则引擎: userId={}, 耗时={}ms, error={}",
                    userId, duration, e.getMessage());
            return null; // 返回null触发规则引擎fallback
        }
    }

    // ==================== 营销Agent推理 ====================

    /**
     * 营销策略推理 - 用户分析 + 策略制定
     */
    public MarketingReasoning reasonMarketing(String userId, Map<String, Object> userProfile,
                                               String scenario) {
        long startTime = System.currentTimeMillis();

        String prompt = """
                ## 营销策略分析任务

                **用户ID:** %s
                **用户画像:** %s
                **场景:** %s

                请分析用户状态并制定营销策略，输出JSON格式：

                ```json
                {
                  "userSegment": "新用户/活跃用户/沉默用户/流失风险用户/高价值用户",
                  "churnRisk": 0-10,
                  "strategy": "策略描述",
                  "actions": [
                    {"type": "ISSUE_COUPON", "amount": 20, "couponType": "FIXED", "reason": "..."},
                    {"type": "SEND_NOTIFICATION", "message": "...", "channel": "APP_PUSH"},
                    {"type": "RECOMMEND_PRODUCT", "category": "...", "reason": "..."}
                  ],
                  "expectedROI": "预期效果",
                  "reasoning": "推理过程"
                }
                ```
                """.formatted(userId, userProfile, scenario);

        try {
            String response = marketingChatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            MarketingReasoning result = parseJson(response, MarketingReasoning.class);
            long duration = System.currentTimeMillis() - startTime;
            log.info("营销LLM推理完成: userId={}, segment={}, churnRisk={}, 耗时={}ms",
                    userId, result.getUserSegment(), result.getChurnRisk(), duration);
            return result;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("营销LLM推理失败，降级到规则引擎: userId={}, 耗时={}ms, error={}",
                    userId, duration, e.getMessage());
            return null;
        }
    }

    // ==================== 运营Agent推理 ====================

    /**
     * 运营决策推理 - 库存/价格/异常处理
     */
    public OperationsReasoning reasonOperations(String scenario, Map<String, Object> data) {
        long startTime = System.currentTimeMillis();

        String prompt = """
                ## 运营决策分析任务

                **场景:** %s
                **数据:** %s

                请分析运营数据并给出决策建议，输出JSON格式：

                ```json
                {
                  "decision": "决策结论",
                  "urgency": "LOW/MEDIUM/HIGH/CRITICAL",
                  "actions": [
                    {"type": "REORDER", "productId": "...", "quantity": 200, "reason": "..."},
                    {"type": "ADJUST_PRICE", "productId": "...", "newPrice": 199, "reason": "..."},
                    {"type": "DELIST_PRODUCT", "productId": "...", "reason": "..."}
                  ],
                  "impact": "影响评估",
                  "reasoning": "推理过程"
                }
                ```
                """.formatted(scenario, data);

        try {
            String response = operationsChatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            OperationsReasoning result = parseJson(response, OperationsReasoning.class);
            long duration = System.currentTimeMillis() - startTime;
            log.info("运营LLM推理完成: decision={}, urgency={}, 耗时={}ms",
                    result.getDecision(), result.getUrgency(), duration);
            return result;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("运营LLM推理失败，降级到规则引擎: 耗时={}ms, error={}", duration, e.getMessage());
            return null;
        }
    }

    /**
     * 通用推理方法 - 适用于任意Agent
     */
    public String reason(ChatClient chatClient, String prompt) {
        try {
            return chatClient.prompt().user(prompt).call().content();
        } catch (Exception e) {
            log.error("LLM推理失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析JSON响应，容错处理
     */
    private <T> T parseJson(String response, Class<T> clazz) {
        try {
            // 提取JSON块（LLM可能在JSON前后添加说明文字）
            String json = extractJson(response);
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            log.warn("JSON解析失败，尝试直接解析: {}", response);
            try {
                return objectMapper.readValue(response, clazz);
            } catch (Exception e2) {
                throw new RuntimeException("无法解析LLM响应: " + response, e2);
            }
        }
    }

    /**
     * 从LLM响应中提取JSON块
     */
    private String extractJson(String response) {
        String trimmed = response.trim();
        // 处理markdown代码块
        if (trimmed.contains("```json")) {
            int start = trimmed.indexOf("```json") + 7;
            int end = trimmed.indexOf("```", start);
            if (end > start) {
                return trimmed.substring(start, end).trim();
            }
        }
        if (trimmed.contains("```")) {
            int start = trimmed.indexOf("```") + 3;
            int end = trimmed.indexOf("```", start);
            if (end > start) {
                return trimmed.substring(start, end).trim();
            }
        }
        // 处理纯JSON
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    // ==================== 推理结果DTO ====================

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CustomerServiceReasoning {
        private String intent;
        private String sentiment;
        private double confidence;
        private boolean needsEscalation;
        private String escalationReason;
        private String reply;
        private List<Map<String, Object>> actions;
        private String reasoning;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MarketingReasoning {
        private String userSegment;
        private int churnRisk;
        private String strategy;
        private List<Map<String, Object>> actions;
        private String expectedROI;
        private String reasoning;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OperationsReasoning {
        private String decision;
        private String urgency;
        private List<Map<String, Object>> actions;
        private String impact;
        private String reasoning;
    }
}
