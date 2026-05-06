package com.ecommerce.agentops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 多Agent协同电商运营自动化系统 - 启动入口
 *
 * 系统架构:
 * - 事件驱动: Agent通过事件总线进行异步通信，松耦合协作
 * - 多Agent协同: 客服、营销、运营、监控四类Agent各司其职
 * - 中心编排: Orchestrator负责复杂业务流程的Agent协调
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class AgentOpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentOpsApplication.class, args);
    }
}
