# 多Agent协同电商运营自动化系统

基于 Spring Boot 的事件驱动多Agent协作系统，面向电商运营场景，实现客服、营销、运营、监控四大业务的自动化协同。

---

## 一、项目解决的核心痛点

电商运营是一个高度跨职能、高频决策、实时响应的业务场景。传统模式下存在以下结构性痛点：

### 痛点1：人力成本居高不下，且线性增长

客服需要7x24在线，运营需要实时盯库存和价格，营销需要持续策划活动，监控需要专人值班。业务规模增长时，人力同步线性扩张。一个中等规模电商团队，仅客服+运营+营销就需要数十人。

**本系统的解法：** 四个Agent各自承担一个职能域的自动化决策，7x24无休运行。客服Agent自动应答80%以上的常见咨询，运营Agent自动补货和调价，营销Agent自动识别流失用户并发放挽回券。人力只需处理Agent无法决策的边界情况。

### 痛点2：跨部门协作断裂，信息孤岛严重

客户投诉时，客服需要手动通知运营查库存、通知营销发补偿券、通知监控记录异常。每个环节都有延迟和信息丢失。一个退款流程可能涉及客服、运营、财务三个部门，来回沟通耗时数小时。

**本系统的解法：** 事件驱动架构让Agent之间实时联动。客服Agent识别到愤怒情绪后，自动发布事件，营销Agent收到后立即发放安抚券，监控Agent同步记录升级告警。整个链路在毫秒级完成，无需人工协调。

### 痛点3：被动响应多，主动预防少

传统运营是"出了问题再处理"——库存卖完了才发现缺货，客户流失了才发现没挽留，价格被竞品碾压了才发现没调整。缺乏持续的、前瞻性的监控和自动干预能力。

**本系统的解法：** 监控Agent持续扫描系统状态（每60秒健康检查、每30分钟流失风险扫描），在问题发生前主动触发干预。库存低于阈值自动补货，用户超过30天未下单自动发放挽回券，价格波动超过10%自动通知营销Agent调整策略。

### 痛点4：决策链路长但缺乏协调

一个"新订单"背后是一条长决策链：验证库存 → 检查优惠券 → 计算价格 → 扣减库存 → 分析复购机会 → 发送确认。人工执行这条链路容易遗漏步骤、顺序出错、中间状态丢失。

**本系统的解法：** 编排引擎（Orchestrator）将这类长链决策抽象为工作流模板，按步骤自动协调多个Agent执行，每一步的状态和结果都被记录和追踪，失败时自动中止并告警。

### 痛点5：重复决策消耗大量运营精力

每天面对相似的决策：这个用户要不要发券？这个商品要不要补货？这个退款要不要批准？这些决策有规律可循，但人工处理时每次都要重新判断，效率极低。

**本系统的解法：** 将高频重复决策规则化。退款金额<100元自动批准，库存<10自动触发补货，愤怒客户自动发放50元安抚券。Agent基于规则和上下文自动决策，运营人员只需维护规则。

---

## 二、核心逻辑流

### 2.1 整体协作模式

本系统采用**事件驱动 + 中心编排**的混合协作模式。Agent之间的通信通过事件总线异步进行（松耦合），而跨Agent的复杂业务流程由编排引擎统一协调（强保障）。

```
                     ┌─────────────────────────────┐
                     │       外部事件/用户操作       │
                     │  (创建订单/发送消息/退款申请)  │
                     └──────────────┬──────────────┘
                                    │
                     ┌──────────────▼──────────────┐
                     │       业务 Service 层        │
                     │  接收请求，发布领域事件        │
                     └──────────────┬──────────────┘
                                    │ publish(event)
                     ┌──────────────▼──────────────┐
                     │     事件总线 (EventBus)       │
                     │  优先级排序 → 异步分发 → 路由   │
                     └───┬──────┬──────┬──────┬─────┘
                         │      │      │      │
            ┌────────────▼┐  ┌──▼──────▼─┐  ┌▼───────────┐
            │  客服Agent   │  │ 编排引擎   │  │  监控Agent  │
            │  感知→决策   │  │           │  │  持续巡检    │
            └──────┬──────┘  │  工作流调度 │  └────────────┘
                   │         │           │
          ┌────────┼─────────┤           │
          │        │         │           │
    ┌─────▼────┐ ┌─▼───────┐ │    ┌──────▼──────┐
    │营销Agent │ │运营Agent│ │    │  事件回流     │
    │联动响应  │ │联动执行 │◄┘    │  (级联触发)   │
    └──────────┘ └─────────┘      └─────────────┘
```

### 2.2 单Agent内部决策链（短链推理）

每个Agent内部遵循 **感知 → 判断 → 决策 → 执行 → 反馈** 的闭环。当前版本基于规则引擎实现，预留了LLM推理的扩展点。

以客服Agent处理一条客户消息为例：

```
接收消息("我的订单什么时候发货？")
    │
    ▼
[感知] 提取上下文：userId、sessionId、消息内容
    │
    ▼
[判断-1] 情绪分析：关键词匹配
    │  → "发货" 不在愤怒/负面词表中
    │  → 情绪判定：NEUTRAL
    │
    ▼
[判断-2] 升级判断：是否需要人工介入？
    │  → 不包含"投诉/退款/差评"等升级关键词
    │  → 情绪不是ANGRY
    │  → 结论：不升级
    │
    ▼
[决策] 生成回复策略
    │  → 匹配关键词"发货" → 使用发货模板
    │  → 或：关联订单号 → 查询订单状态 → 动态回复
    │
    ▼
[执行] 发布 CUSTOMER_SERVICE_REPLY 事件
    │  → payload: {reply: "您的订单正在加急处理中...", sentiment: "NEUTRAL"}
    │
    ▼
[反馈] 记录消息到会话历史，更新Agent指标
```

**升级为LLM推理后的变化：** 当接入大语言模型后，判断和决策步骤将从关键词匹配升级为语义理解和多步推理。例如："我的订单三天了还没发货，而且上次买的耳机也有问题" → LLM识别出这是一个复合诉求（延迟发货+质量问题），情绪为"不满"，需要同时查询两个订单并给出补偿方案。这就是单Agent内的**长链推理**。

### 2.3 多Agent协作流（跨Agent长链）

系统中存在两种跨Agent协作模式：

#### 模式A：编排驱动的顺序链（Orchestrator模式）

由编排引擎预定义工作流，按步骤依次调度不同Agent。适用于流程固定、步骤明确的业务场景。

**示例：新订单处理工作流（4步链）**

```
用户创建订单
    │
    ▼
[编排引擎] 启动工作流 "new-order-processing"
    │
    ├─ Step 1 ──→ [运营Agent] 验证库存
    │   │         检查商品P001库存=200，充足
    │   │         发布 WORKFLOW_STEP_COMPLETED(success=true)
    │   ▼
    ├─ Step 2 ──→ [营销Agent] 应用优惠券
    │   │         检查用户U001是否有可用券
    │   │         发现满100减20券，应用折扣
    │   │         发布 WORKFLOW_STEP_COMPLETED(success=true)
    │   ▼
    ├─ Step 3 ──→ [运营Agent] 计算价格
    │   │         商品999 - 优惠20 = 实付979
    │   │         发布 WORKFLOW_STEP_COMPLETED(success=true)
    │   ▼
    └─ Step 4 ──→ [客服Agent] 发送确认
                    向用户发送订单确认消息
                    工作流完成 ✓
```

**示例：退款处理工作流（5步链，含跨Agent联动）**

```
用户申请退款
    │
    ▼
[编排引擎] 启动工作流 "refund-processing"
    │
    ├─ Step 1 ──→ [客服Agent] 验证退款资格
    │   │         检查订单状态、退款原因、金额
    │   │         小额(<100元)自动通过，大额需人工审批
    │   ▼
    ├─ Step 2 ──→ [运营Agent] 处理退款
    │   │         执行退款操作
    │   ▼
    ├─ Step 3 ──→ [运营Agent] 恢复库存
    │   │         将退款商品数量加回库存
    │   ▼
    ├─ Step 4 ──→ [营销Agent] 发放补偿券
    │   │         为退款用户发放30元无门槛券（挽回流失）
    │   ▼
    └─ Step 5 ──→ [客服Agent] 通知客户
                    通知用户退款已到账+补偿券已发放
                    工作流完成 ✓
```

#### 模式B：事件驱动的级联链（级联模式）

没有预定义流程，Agent根据事件自发响应，形成事件级联。适用于需要灵活响应、动态决策的场景。

**示例：客户投诉引发的级联响应**

```
客户发送消息："你们太垃圾了！我要投诉到消协！"
    │
    ▼
[客服Agent] 接收消息
    │  情绪分析 → ANGRY（检测到"垃圾""投诉""消协"）
    │  判断 → 需要升级（情绪+关键词双重触发）
    │
    ├──→ 发布 CUSTOMER_SERVICE_ESCALATION (CRITICAL)
    │       → 通知人工客服系统
    │
    ├──→ 发布 CUSTOMER_EMOTION_ESCALATION (HIGH)
    │       │
    │       ▼
    │    [营销Agent] 接收情绪升级事件
    │       │  判断：ANGRY情绪 → 发放高额度安抚券
    │       │  发放50元安抚券给用户
    │       │
    │       ├──→ 发布 COUPON_ISSUED
    │       │       → 记录到用户优惠券账户
    │       │
    │       └──→ 发布 PRICE_CHANGE (类型=CHURN_RECALL)
    │               → 生成召回通知文案
    │
    ├──→ 发布 ORDER_ANOMALY (HIGH)
    │       │
    │       ▼
    │    [运营Agent] 接收订单异常事件
    │       │  记录异常，关联订单
    │       │
    │       └──→ 发布 BUSINESS_METRIC_ALERT
    │               │
    │               ▼
    │            [监控Agent] 接收业务指标告警
    │               记录告警，更新系统报表
    │
    └──→ [监控Agent] 直接接收 ESCALATION 事件
            生成 SERVICE_ESCALATION 告警
            记入告警历史
```

这条级联链中，一个客户消息在毫秒级内触发了 3 个Agent、6+ 个事件的连锁响应，全程无人工介入。

**示例：库存耗尽的级联响应**

```
订单支付成功 → 运营Agent扣减库存
    │
    ▼
[运营Agent] 检查库存水位
    │  P002(无线耳机) 库存: 8 → 5
    │  判断: 5 < 10 (CRITICAL阈值)
    │
    ├──→ 发布 STOCK_DEPLETED (CRITICAL)
    │       │
    │       ├─ [运营Agent自身] 消费
    │       │   商品自动下架
    │       │   触发自动补货(补200件)
    │       │
    │       │   ┌─ 发布 AUTO_REORDER_TRIGGERED
    │       │   │   创建采购单 → 通知供应商
    │       │   │
    │       │   └─ 发布 PRODUCT_DELISTED
    │       │       商品从前端下架
    │       │
    │       └─ [编排引擎] 消费
    │           启动 "stock-replenishment" 工作流
    │           (如果需要更复杂的补货流程)
    │
    ├──→ 发布 STOCK_WARNING (HIGH)
    │       │
    │       ▼
    │    [监控Agent] 消费
    │       生成 STOCk_LOW 预警告警
    │
    └──→ [监控Agent] 消费 STOCK_DEPLETED
            生成 STOCK_OUT 严重告警
            推送告警通知
```

### 2.4 协作模式对比

| 维度 | 编排驱动 (Orchestrator) | 事件级联 (Event Cascade) |
|------|------------------------|------------------------|
| 流程确定性 | 高，步骤预定义 | 低，Agent自发响应 |
| 适用场景 | 流程固定：订单处理、退款 | 灵活响应：投诉、异常 |
| 失败处理 | 编排引擎统一中止/重试 | 各Agent独立处理 |
| 扩展性 | 新增步骤需修改工作流定义 | 新增Agent只需订阅事件 |
| 当前系统中的应用 | 4个工作流模板 | 所有Agent间的实时联动 |

### 2.5 推理能力的现状与演进

当前版本的Agent决策基于**规则引擎 + 关键词匹配**，属于确定性推理。系统已预留LLM集成点，演进路径如下：

```
阶段1（当前）          阶段2                    阶段3
规则引擎              LLM增强                  全LLM推理
─────────            ─────────               ─────────
关键词匹配情绪分析     LLM语义情绪分析           LLM多步推理
if-else退款审批       LLM+规则混合审批          LLM自主决策审批
模板化客服回复        LLM生成个性化回复         LLM上下文对话
固定工作流步骤        LLM动态调整步骤           LLM自主编排

升级方式:             升级方式:                 升级方式:
- 现有规则不变         - 替换handleEvent()       - Agent内部集成
- 作为LLM的fallback   - 中调用LLM API           - ReAct/CoT推理链
                       - 规则作为guardrail
```

**升级为长链推理的示例（客服Agent）：**

```
当前（规则）:
  消息 → 关键词匹配 → 模板回复
  1步决策，<1ms

升级后（LLM长链推理）:
  消息 → [LLM] 语义理解意图
       → [LLM] 判断是否需要查订单（需要）
       → [Tool] 调用OrderService查询订单
       → [LLM] 分析订单状态，判断物流是否异常
       → [Tool] 调用物流API查询
       → [LLM] 综合判断，生成安抚+补偿方案
       → [决策] 是否需要升级人工
       → [执行] 回复+发券+记录
  5-8步推理链，200-2000ms
```

### 2.6 事件流全景图

下图展示了系统中所有事件类型及其流向关系：

```
外部触发                   Agent内部决策                 级联响应
─────────                 ─────────────               ─────────
                          客服Agent
CUSTOMER_INQUIRY ───────→ 情绪分析/自动回复 ──────────→ CUSTOMER_SERVICE_REPLY
CUSTOMER_COMPLAINT ─────→ 识别投诉/触发升级 ──────────→ CUSTOMER_EMOTION_ESCALATION ──→ 营销Agent发券
ORDER_REFUND_REQUEST ───→ 小额自动/大额升级 ──────────→ ORDER_REFUND_COMPLETED
                                                                │
                          运营Agent                              ▼
ORDER_PAID ─────────────→ 扣减库存/检查水位 ──────────→ STOCK_WARNING / STOCK_DEPLETED
STOCK_DEPLETED ─────────→ 自动下架/触发补货 ──────────→ AUTO_REORDER_TRIGGERED
PRICE_ADJUSTED ─────────→ 记录历史/检查波动 ──────────→ PRICE_CHANGE ──→ 营销Agent
                                                                │
                          营销Agent                              ▼
ORDER_CREATED ──────────→ 分析新用户/首单券 ──────────→ COUPON_ISSUED
CHURN_RISK_DETECTED ────→ 发放挽回券/召回通知 ────────→ PRICE_CHANGE(type=CHURN_RECALL)
COUPON_ISSUED ──────────→ 记录到用户账户
                                                                │
                          监控Agent                              ▼
*所有告警事件 ──────────→ 聚合/分级/记录 ─────────────→ 告警历史
BUSINESS_METRIC_ALERT ──→ 指标异常检测
AGENT_STATUS_CHANGED ───→ Agent异常告警
```

---

## 三、系统架构

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

## 四、核心特性

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

## 五、快速开始

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

## 六、API 列表

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

## 七、典型场景演示

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

## 八、项目结构

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

## 九、扩展指南

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

### 集成LLM实现长链推理
1. 在Agent的 `handleEvent()` 中替换规则逻辑为LLM API调用
2. 使用ReAct模式实现多步推理：Thought → Action → Observation → Thought → ...
3. 将现有规则作为guardrail/fallback，确保LLM异常时有兜底
4. 推荐框架：LangChain4j / Spring AI

### 切换事件总线
当前使用内存事件总线（`InMemoryEventBus`），生产环境可替换为：
- Kafka: 高吞吐、持久化、消息回溯
- RabbitMQ: 灵活路由、可靠投递
- RocketMQ: 事务消息、延迟消息

只需实现 `EventBus` 接口即可无缝切换。
