# AI 理财投资助手（Tree-of-Thought AI Agent）

一个全栈的 AI 投资/理财助手：管理自选股票/基金、记录买卖并实时计算历史盈亏；通过 **RAG + 思维树（ToT）+ ReAct + 多轮投票** 的 AI Agent 预测单只标的涨跌并给出增持/减持建议；输入工资由 AI 规划如何理财（保守/均衡/激进）；还能基于收入、余额与理财盈亏一键汇总分析，并结合财经新闻与用户意向给出可视化的理财建议。

- 前端：React 18 + Vite
- 后端：Spring Boot 3（Java 21），**AI 部分基于 [LangChain4j](https://github.com/langchain4j/langchain4j) 1.16.2**（含本地嵌入模型构建的 RAG）
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
5. **理财分析与建议（新增）**
   - 维护可编辑的**现金余额**与收入/支出档案；
   - **一键汇总分析**：后端精确计算净资产、投资市值、盈亏、储蓄率等指标，AI 负责文字解读与关键发现、健康分；
   - **意向建议**：结合财务汇总、**爬取的财经新闻**与**用户填写的理财意向**，输出健康分、风险等级、优势/风险、资产配置（当前→建议）、行动清单与新闻洞察，前端可视化展示。

### AI 实现说明（LangChain4j）

所有 AI 调用统一经由 `AiClient`（`service/ai/AiClient.java`），其内部使用 **LangChain4j** 的 `OpenAiChatModel` 按用户在页面填写的 `url/key/model` **动态构建**模型并缓存，对上层 Agent 屏蔽实现细节。因此预测、理财规划、理财分析三类 Agent 均运行在 LangChain4j 之上，可对接任意 OpenAI 兼容服务。

### RAG（检索增强）+ ReAct

- **RAG 知识库**（`service/rag/RagService.java`）：将「历史理财情况」与「核心股票/基金波动」整理为源文档（整体财务、收入、各资产的**波动特征**——区间涨跌/日波动率/最大回撤/趋势、各资产持仓盈亏、历史预测、历史规划），用 **LangChain4j 本地嵌入模型 AllMiniLM-L6-v2（ONNX，离线）** 向量化后存入 `InMemoryEmbeddingStore`。
  - 源文档持久化于 `rag_document` 表，向量索引在内存重建。
  - 接口：`POST /rag/rebuild` 重建、`GET /rag/documents` 查看、`POST /rag/search` 调试检索。
  - **预测时会先从 RAG 检索**该标的最相关的资料，注入到思维树与 ReAct 推理中。
- **ReAct 引擎**（`service/ai/react/ReActEngine.java`）：以「思考(Thought) → 行动(Action) → 观察(Observation)」循环推进。预测的每一轮裁决都是一次 ReAct episode，模型可调用工具 `RAG_SEARCH`（检索知识库）与 `NEWS_SEARCH`（爬取财经新闻）补充证据后再下结论。
  - **优先使用原生 function-calling**：`AiClient.supportsToolCalling()` 会探测并缓存模型/服务是否支持工具调用；支持则用 LangChain4j 的 `ToolSpecification` + `ToolExecutionResultMessage` 让模型自行决定调用工具；
  - **自动回退**：若不支持工具调用（探测报错）或调用过程异常，则回退到基于 JSON 协议的手动 ReAct，兼容任意 OpenAI 兼容模型。
- **多轮验证与投票**：对单标的进行 N 轮 ReAct 裁决，多数派占比 **> 60%** 才确认最终结论，提升分析稳定性。

> 数据准备：可在「资产看板」录入价格历史（每行 `日期,收盘价`）以生成核心波动文档，使 RAG 的波动分析更准确。

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
│       │   ├── ai/           # AI 客户端(LangChain4j)
│       │   │   ├── tot/      # 思维树 Agent: PredictionAgent / FinancePlanAgent
│       │   │   └── react/    # ReAct 引擎(思考-行动-观察)
│       │   ├── rag/          # RAG 服务(本地嵌入 + 内存向量库)
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
| GET/POST | `/advisor/profile` | 财务档案(现金余额等) |
| POST | `/advisor/summary` | 一键汇总分析理财信息 |
| POST | `/advisor/advice` | 基于新闻与意向的理财建议 |
| GET | `/advisor/reports` | 历史分析报告 |
| POST | `/rag/rebuild` | 重建 RAG 向量索引 |
| GET | `/rag/documents` | 查看 RAG 源文档 |
| POST | `/rag/search` | 调试 RAG 检索 |
| GET/POST | `/assets/{id}/price-history` | 资产价格历史(核心波动) |

---

## 说明

- 新闻爬虫在无外网或目标站点结构变化时会**优雅降级**为空，Agent 会基于历史数据继续推理。
- 所有 AI 调用失败均有兜底逻辑，不会导致接口整体崩溃。
- 数据库精度以 `sql/schema.sql` 为准（金额 `DECIMAL`），生产环境请使用 MySQL profile（默认）。
