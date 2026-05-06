# 多Agent协同电商运营自动化系统

基于 Spring Boot 的事件驱动多Agent协作系统，面向电商运营场景，实现客服、营销、运营、监控四大业务的自动化协同。

## 系统架构

```
┌──────────────────────────────────────────────────────────────────┐
│                        REST API 层                               │
│  OrderController | CustomerController | AgentController | ...    │
└──────────┬───────────────────────────────────────────────────────┘
           │
┌──────────▼───────────────────────────────────────────────────────┐
│                     业务 Service 层                               │
│  OrderService | CustomerSessionService | UserService | ...       │
└──────────┬───────────────────────────────────────────────────────┘
           │
┌──────────▼───────────────────────────────────────────────────────┐
│                    Agent 编排引擎                                  │
│  AgentOrchestrator（工作流调度、多Agent协调）                       │
└──────────┬───────────────────────────────────────────────────────┘
           │
┌──────────▼───────────────────────────────────────────────────────┐
│                  Agent 智能决策层                                  │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────┐│
│  │  客服Agent    │ │  营销Agent    │ │  运营Agent    │ │ 监控Agent ││
│  │  自动应答     │ │  优惠券发放   │ │  库存管理     │ │ 健康检查  ││
│  │  情绪识别     │ │  流失预警     │ │  价格管理     │ │ 异常检测  ││
│  │  退款处理     │ │  复购推荐     │ │  自动补货     │ │ 告警管理  ││
│  └──────┬───────┘ └──────┬───────┘ └──────┬───────┘ └────┬─────┘│
└─────────┼───────────────┼───────────────┼───────────────┼────────┘
          │               │               │               │
┌─────────▼───────────────▼───────────────▼───────────────▼────────┐
│                    事件总线 EventBus                               │
│           InMemoryEventBus（优先级队列 + 异步分发）                │
└──────────────────────────────────────────────────────────────────┘
```

## 核心特性

### 事件驱动架构
- Agent之间通过事件总线进行异步通信，松耦合
- 支持事件优先级（CRITICAL > HIGH > NORMAL > LOW）
- 支持点对点和广播两种事件分发模式

### 四大业务Agent
1. **客服Agent** - 智能应答、情绪识别、退款处理、人工转接
2. **营销Agent** - 优惠券管理、流失预警、复购推荐、活动管理
3. **运营Agent** - 库存监控、价格管理、自动补货、异常处理
4. **监控Agent** - 健康检查、异常检测、告警管理、报表生成

### 编排引擎
- 预定义工作流模板（新订单处理、退款处理、流失挽回、库存补货）
- 工作流步骤的顺序执行和状态跟踪
- 基于事件触发的自动工作流启动

## 快速开始

### 环境要求
- JDK 17+
- Maven 3.8+

### 编译运行
```bash
cd multi-agent-ecommerce-ops
mvn clean package -DskipTests
java -jar target/agent-ops-hub-1.0.0-SNAPSHOT.jar
```

### API 端口: 8080

## API 列表

### 订单管理
```bash
# 创建订单（触发新订单处理工作流）
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"userId":"U001","userName":"张三","productId":"P001","productName":"智能手表Pro","quantity":1,"price":999,"shippingAddress":"北京市朝阳区"}'

# 支付订单（触发库存扣减、营销分析等）
curl -X POST http://localhost:8080/api/orders/ORD-xxx/pay

# 申请退款（触发退款处理工作流）
curl -X POST http://localhost:8080/api/orders/ORD-xxx/refund \
  -H "Content-Type: application/json" \
  -d '{"reason":"不想要了"}'

# 查询订单
curl http://localhost:8080/api/orders/ORD-001
curl http://localhost:8080/api/orders/user/U001
```

### 客服交互
```bash
# 发送客服消息（触发智能自动回复）
curl -X POST http://localhost:8080/api/customer/message \
  -H "Content-Type: application/json" \
  -d '{"userId":"U001","message":"我的订单什么时候发货？"}'

# 触发情绪升级（测试人工转接）
curl -X POST http://localhost:8080/api/customer/message \
  -H "Content-Type: application/json" \
  -d '{"userId":"U001","message":"你们太过分了，我要投诉！"}'
```

### Agent管理
```bash
# 查看所有Agent状态
curl http://localhost:8080/api/agents/status

# 查看系统指标
curl http://localhost:8080/api/agents/metrics

# 启停Agent
curl -X POST http://localhost:8080/api/agents/customer-service-agent/stop
curl -X POST http://localhost:8080/api/agents/customer-service-agent/start
```

### 监控告警
```bash
# 系统概览
curl http://localhost:8080/api/monitoring/overview

# 查看告警
curl http://localhost:8080/api/monitoring/alerts
curl http://localhost:8080/api/monitoring/alerts/level/CRITICAL
```

### 工作流
```bash
# 查看工作流模板
curl http://localhost:8080/api/orchestrator/templates

# 查看活跃工作流
curl http://localhost:8080/api/orchestrator/workflows

# 手动启动工作流
curl -X POST http://localhost:8080/api/orchestrator/workflows/start/churn-recovery \
  -H "Content-Type: application/json" \
  -d '{"userId":"U002"}'
```

### 营销
```bash
# 查看用户优惠券
curl http://localhost:8080/api/marketing/coupons/U001

# 查看流失风险用户
curl http://localhost:8080/api/marketing/churn-risk
```

## 典型场景演示

### 场景1: 新订单全自动处理
1. `POST /api/orders` 创建订单 → 触发工作流 `new-order-processing`
2. 编排引擎自动协调：运营Agent验证库存 → 营销Agent应用优惠 → 运营Agent计算价格 → 客服Agent发送确认
3. `POST /api/orders/{id}/pay` 支付 → 运营Agent扣减库存 → 营销Agent分析复购机会 → 监控Agent记录指标

### 场景2: 客户情绪升级自动应对
1. 客户发送"你们太垃圾了，我要投诉！" → 客服Agent识别到愤怒情绪
2. 自动触发人工转接 → 同时通知营销Agent发放50元安抚券
3. 监控Agent记录升级告警

### 场景3: 库存耗尽自动补货
1. 订单支付扣减库存后，运营Agent检测到库存 < 10
2. 自动发布 STOCK_DEPLETED 事件 → 商品自动下架
3. 触发工作流 `stock-replenishment`：检查库存 → 创建采购单 → 通知供应商 → 监控到货
4. 监控Agent发出 CRITICAL 告警

## 项目结构

```
src/main/java/com/ecommerce/agentops/
├── AgentOpsApplication.java          # 启动入口
├── config/                            # 配置
│   ├── AsyncConfig.java              # 线程池配置
│   ├── AgentConfig.java              # Agent配置
│   └── AgentSystemInitializer.java   # 系统初始化
├── event/                             # 事件系统
│   ├── BaseEvent.java                # 事件基类
│   ├── EventBus.java                 # 事件总线接口
│   ├── DomainEvents.java             # 事件类型定义
│   └── impl/InMemoryEventBus.java    # 内存事件总线实现
├── agent/                             # Agent层
│   ├── core/                          # Agent核心抽象
│   │   ├── Agent.java                # Agent接口
│   │   ├── BaseAgent.java            # Agent基类
│   │   ├── AgentContext.java         # Agent上下文
│   │   ├── AgentRegistry.java        # Agent注册中心
│   │   ├── AgentStatus.java          # Agent状态枚举
│   │   └── AgentMetrics.java         # Agent指标
│   ├── customer/CustomerServiceAgent  # 客服Agent
│   ├── marketing/MarketingAgent       # 营销Agent
│   ├── operations/OperationsAgent     # 运营Agent
│   ├── monitoring/MonitoringAgent     # 监控Agent
│   └── orchestrator/AgentOrchestrator # 编排引擎
├── model/                             # 数据模型
│   ├── entity/                        # 实体
│   ├── enums/                         # 枚举
│   └── dto/                           # DTO
├── service/                           # 业务服务
│   ├── OrderService.java
│   ├── UserService.java
│   └── CustomerSessionService.java
├── controller/                        # REST API
│   ├── OrderController.java
│   ├── CustomerController.java
│   ├── AgentController.java
│   ├── MonitoringController.java
│   ├── OrchestratorController.java
│   └── MarketingController.java
└── util/GlobalExceptionHandler.java   # 异常处理
```

## 扩展指南

### 新增Agent类型
1. 继承 `BaseAgent`，实现 `handleEvent()` 等抽象方法
2. 在 `getSubscribedEventTypes()` 中声明订阅的事件
3. 用 `@Component` 注解注册为Spring Bean
4. 在 `AgentSystemInitializer` 中添加注册

### 新增事件类型
1. 在 `DomainEvents` 中定义事件类型常量
2. 在需要发布该事件的Agent中调用 `context.publishEvent()`
3. 在需要消费该事件的Agent中订阅

### 新增工作流
1. 在 `AgentOrchestrator.initWorkflowTemplates()` 中定义步骤
2. 每个步骤指定目标Agent和步骤名称
3. 工作流可通过事件自动触发或API手动启动

### 切换事件总线
当前使用内存事件总线（`InMemoryEventBus`），生产环境可替换为：
- Kafka: 高吞吐、持久化、消息回溯
- RabbitMQ: 灵活路由、可靠投递
- RocketMQ: 事务消息、延迟消息

只需实现 `EventBus` 接口即可无缝切换。
