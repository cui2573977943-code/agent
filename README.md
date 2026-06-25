# AI 理财投资助手（Tree-of-Thought AI Agent）

一个全栈的 AI 投资/理财助手：管理自选股票/基金、记录买卖并实时计算历史盈亏；通过**思维树（Tree-of-Thought）AI Agent**预测单只标的涨跌并给出增持/减持建议；输入工资由 AI 规划如何理财（保守/均衡/激进）。

- 前端：React 18 + Vite
- 后端：Spring Boot 3（Java 21）
- 数据库：MySQL 8（附建表脚本）

---

## 功能特性

1. **AI 模型接入（页面可配置）**
   - 在「AI 配置」页填写任意 **OpenAI 兼容接口** 的 `url` 与 `key`（OpenAI / DeepSeek / Moonshot / 通义千问 / 本地 Ollama 等均可），并可一键测试连通性。
2. **资产看板**
   - 添加自选股票/基金，录入买入/卖出交易，自动维护持仓份额、平均成本。
   - 实时展示当前市值、浮动盈亏、已实现盈亏、总盈亏率与组合汇总。
3. **AI 涨势预测（思维树 + 多轮验证）**
   - ① 思维链（CoT）拆分分析维度；
   - ② 读取**历史价格/交易**信息并**爬取最新相关新闻**；
   - ③ 围绕每个维度展开思维树多分支并打分；
   - ④ 进行**多轮独立裁决**；
   - ⑤ 多数派占比 **超过 60%** 才确认为最终结论，否则判为「不确定」；
   - ⑥ 结论映射操作建议：看涨→增持、看跌→减持、不确定→观望。
4. **AI 理财规划（思维树 + 多轮验证）**
   - 同样先思维链拆分，再结合**历史理财盈亏**选择**保守/均衡/激进**风格（历史盈利→可更激进，历史亏损→更保守）；
   - 多轮验证确定风格后，给出应急金、可投资金额，以及**买哪个基金/原有持仓增持或减持**的落地方案。

---

## 目录结构

```
.
├── sql/
│   └── schema.sql            # MySQL 建表脚本（含示例数据）
├── backend/                  # Spring Boot 后端
│   ├── pom.xml
│   └── src/main/java/com/aifinance/
│       ├── controller/       # REST 接口
│       ├── service/          # 业务编排
│       │   ├── ai/           # AI 客户端 + 思维树 Agent
│       │   │   └── tot/      # PredictionAgent / FinancePlanAgent
│       │   └── crawler/      # 新闻爬虫（Jsoup，优雅降级）
│       ├── entity/           # JPA 实体
│       ├── repository/       # 数据访问
│       ├── dto/ · common/ · config/
│       └── resources/application.yml
└── frontend/                 # React 前端
    ├── package.json
    └── src/{pages,components,api.js,...}
```

---

## 快速开始

### 1. 初始化数据库

```bash
mysql -u root -p < sql/schema.sql
```

默认数据库名 `ai_finance`。可通过环境变量覆盖连接信息：`DB_HOST`、`DB_PORT`、`DB_USER`、`DB_PASSWORD`。

### 2. 启动后端

```bash
cd backend
mvn spring-boot:run
# 默认端口 8080，上下文路径 /api
```

> 无 MySQL 时可用内存库快速体验：`mvn spring-boot:run -Dspring-boot.run.profiles=h2`

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
# 访问 http://localhost:5173 （已配置 /api 代理到后端 8080）
```

### 4. 使用流程

1. 打开「AI 配置」，填写模型 `url` 与 `key`，保存并测试连接。
2. 在「资产看板」添加股票/基金，录入买卖交易，查看盈亏。
3. 在「AI 涨势预测」选择标的，点击预测，查看思维树推理与多轮裁决结果。
4. 在「AI 理财规划」输入工资与支出，生成理财方案。

---

## 思维树 Agent 设计

`PredictionAgent` 与 `FinancePlanAgent` 均实现「拆分 → 展开 → 多轮验证 → 聚合确认」：

| 步骤 | 预测 Agent | 理财 Agent |
| --- | --- | --- |
| 拆分(CoT) | 价格趋势/新闻情绪/宏观/资金面/风险… | 应急金/消费负债/风险偏好/资产配置… |
| 展开(ToT) | 每维度生成多条候选思路并打分 | 每维度生成保守/均衡/激进候选策略 |
| 上下文 | 历史价格+交易+爬虫新闻 | 历史理财盈亏+持仓+候选标的 |
| 多轮验证 | N 轮裁 UP/DOWN | N 轮裁 保守/均衡/激进 |
| 确认阈值 | 多数派 > 60% 才确认 | 多数派 > 60% 才确认 |
| 输出 | 结论 + 增持/减持 + 置信度 | 风格 + 应急金/可投金额 + 增持/减持方案 |

相关参数可在 `application.yml` 的 `agent.*` 调整（轮数 `rounds`、阈值 `confidence-threshold`、分支数 `branches`、爬虫开关 `crawler.enabled` 等）。

---

## 主要 REST 接口（前缀 `/api`）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET/POST | `/ai-config` | 获取/保存 AI 配置 |
| POST | `/ai-config/test` | 测试连通性 |
| GET/POST/DELETE | `/assets` | 资产增查删 |
| GET | `/holdings` · `/holdings/summary` | 持仓与组合汇总 |
| POST | `/holdings/transactions` | 记录买入/卖出 |
| POST | `/predictions` | 发起思维树预测 |
| GET | `/predictions` | 历史预测记录 |
| POST | `/finance/plan` | 生成理财规划 |
| GET/POST | `/finance/salary` | 工资记录 |

---

## 说明

- 新闻爬虫在无外网或目标站点结构变化时会**优雅降级**为空，Agent 会基于历史数据继续推理。
- 所有 AI 调用失败均有兜底逻辑，不会导致接口整体崩溃。
- 数据库精度以 `sql/schema.sql` 为准（金额 `DECIMAL`），生产环境请使用 MySQL profile（默认）。
