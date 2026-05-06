package com.ecommerce.agentops.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 配置
 *
 * 基于 OpenAI 兼容协议，可对接:
 * - OpenAI (GPT-4o / GPT-4)
 * - 国产大模型 (通义千问 / 智谱 / DeepSeek / Moonshot 等，均兼容 OpenAI API)
 * - 本地模型 (Ollama / vLLM / LocalAI)
 *
 * 只需修改 application.yml 中的 base-url 和 api-key 即可切换模型
 */
@Configuration
public class AiConfig {

    /**
     * 客服Agent专用 ChatClient - 带对话记忆
     */
    @Bean("customerServiceChatClient")
    public ChatClient customerServiceChatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("""
                        你是一个专业的电商客服AI助手。你的职责是：
                        1. 理解客户意图（咨询、投诉、退款、查询等）
                        2. 分析客户情绪（正面、中性、负面、愤怒）
                        3. 判断是否需要人工介入
                        4. 生成专业、温暖的回复

                        回复要求：简洁专业，表达同理心，提供具体解决方案。
                        如果客户情绪愤怒或涉及投诉、退款金额>100元，必须建议转人工。
                        """)
                .defaultAdvisors(new MessageChatMemoryAdvisor(new InMemoryChatMemory()))
                .build();
    }

    /**
     * 营销Agent专用 ChatClient
     */
    @Bean("marketingChatClient")
    public ChatClient marketingChatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("""
                        你是一个电商营销策略AI助手。你的职责是：
                        1. 分析用户画像和购买行为
                        2. 判断用户流失风险等级
                        3. 制定个性化营销策略（优惠券额度、活动推荐）
                        4. 评估营销投入产出比

                        输出格式：JSON结构化数据，包含decision、reasoning、actions字段。
                        """)
                .build();
    }

    /**
     * 运营Agent专用 ChatClient
     */
    @Bean("operationsChatClient")
    public ChatClient operationsChatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("""
                        你是一个电商运营决策AI助手。你的职责是：
                        1. 分析库存数据，判断补货优先级
                        2. 分析价格变动，评估竞争策略
                        3. 处理订单异常，制定解决方案
                        4. 优化运营效率

                        输出格式：JSON结构化数据，包含decision、reasoning、actions字段。
                        """)
                .build();
    }
}
