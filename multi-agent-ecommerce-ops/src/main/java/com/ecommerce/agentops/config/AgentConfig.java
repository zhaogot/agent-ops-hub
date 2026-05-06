package com.ecommerce.agentops.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent系统全局配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "agent-ops")
public class AgentConfig {

    private EventBusConfig eventBus = new EventBusConfig();
    private AgentsConfig agents = new AgentsConfig();

    @Data
    public static class EventBusConfig {
        private int threadPoolSize = 8;
        private int queueCapacity = 10000;
    }

    @Data
    public static class AgentsConfig {
        private AgentProperties customerService = new AgentProperties();
        private AgentProperties marketing = new AgentProperties();
        private AgentProperties operations = new AgentProperties();
        private AgentProperties monitoring = new AgentProperties();
    }

    @Data
    public static class AgentProperties {
        private boolean enabled = true;
        private int maxConcurrentTasks = 10;
        private Map<String, Object> extra = new HashMap<>();
    }
}
