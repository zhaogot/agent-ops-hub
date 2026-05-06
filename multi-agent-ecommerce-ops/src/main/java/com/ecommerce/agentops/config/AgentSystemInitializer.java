package com.ecommerce.agentops.config;

import com.ecommerce.agentops.agent.core.Agent;
import com.ecommerce.agentops.agent.core.AgentRegistry;
import com.ecommerce.agentops.agent.customer.CustomerServiceAgent;
import com.ecommerce.agentops.agent.marketing.MarketingAgent;
import com.ecommerce.agentops.agent.monitoring.MonitoringAgent;
import com.ecommerce.agentops.agent.operations.OperationsAgent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Agent系统初始化器
 *
 * 在Spring容器就绪后:
 * 1. 注册所有Agent到注册中心
 * 2. 按依赖顺序启动Agent（监控Agent最后启动）
 * 3. 输出系统就绪信息
 */
@Slf4j
@Component
public class AgentSystemInitializer {

    private final AgentRegistry agentRegistry;
    private final CustomerServiceAgent customerServiceAgent;
    private final MarketingAgent marketingAgent;
    private final OperationsAgent operationsAgent;
    private final MonitoringAgent monitoringAgent;

    public AgentSystemInitializer(AgentRegistry agentRegistry,
                                   CustomerServiceAgent customerServiceAgent,
                                   MarketingAgent marketingAgent,
                                   OperationsAgent operationsAgent,
                                   MonitoringAgent monitoringAgent) {
        this.agentRegistry = agentRegistry;
        this.customerServiceAgent = customerServiceAgent;
        this.marketingAgent = marketingAgent;
        this.operationsAgent = operationsAgent;
        this.monitoringAgent = monitoringAgent;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(1)
    public void initializeAgents() {
        log.info("========================================");
        log.info("  多Agent电商运营自动化系统  ");
        log.info("========================================");

        // 1. 初始化所有Agent
        List<Agent> agents = List.of(
                customerServiceAgent,
                marketingAgent,
                operationsAgent,
                monitoringAgent
        );

        // 2. 注册到注册中心
        agents.forEach(agent -> {
            agent.initialize();
            agentRegistry.register(agent);
        });

        // 3. 按顺序启动（监控Agent最后启动，确保能监控到其他Agent的启动过程）
        for (Agent agent : agents) {
            try {
                agent.start();
                log.info("[OK] {} 已启动 - {}", agent.getAgentName(), agent.getStatus().getDisplayName());
            } catch (Exception e) {
                log.error("[FAIL] {} 启动失败: {}", agent.getAgentName(), e.getMessage());
            }
        }

        // 4. 输出系统状态
        log.info("========================================");
        log.info("系统启动完成!");
        log.info("已注册Agent: {}", agentRegistry.getAllAgents().size());
        log.info("运行中Agent: {}", agentRegistry.getAgentsByStatus(
                com.ecommerce.agentops.agent.core.AgentStatus.RUNNING).size());
        log.info("API端口: 8080");
        log.info("========================================");
        log.info("可用API:");
        log.info("  POST /api/orders          - 创建订单");
        log.info("  POST /api/orders/{{id}}/pay - 支付订单");
        log.info("  POST /api/orders/{{id}}/refund - 申请退款");
        log.info("  POST /api/customer/message - 发送客服消息");
        log.info("  GET  /api/agents/status    - 查看Agent状态");
        log.info("  GET  /api/monitoring/overview - 系统概览");
        log.info("  GET  /api/monitoring/alerts - 查看告警");
        log.info("  GET  /api/orchestrator/workflows - 查看工作流");
        log.info("========================================");
    }
}
