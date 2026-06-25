-- =====================================================================
-- AI 理财投资助手 数据库建表脚本
-- 数据库: MySQL 8.0+
-- 字符集: utf8mb4
-- 说明: 包含 AI 接入配置、资产、持仓、交易盈亏、价格历史、
--       爬取新闻、AI 预测记录、理财规划记录 等表
-- =====================================================================

CREATE DATABASE IF NOT EXISTS ai_finance
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE ai_finance;

-- ---------------------------------------------------------------------
-- 1. AI 模型接入配置表
--    用户在页面上填写的 AI url / key / 模型名称
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS ai_config;
CREATE TABLE ai_config (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    name            VARCHAR(100)    NOT NULL DEFAULT '默认配置' COMMENT '配置名称',
    base_url        VARCHAR(500)    NOT NULL COMMENT 'AI 接口地址(OpenAI 兼容), 例如 https://api.openai.com/v1',
    api_key         VARCHAR(500)    NOT NULL COMMENT 'AI 接口密钥',
    model           VARCHAR(100)    NOT NULL DEFAULT 'gpt-4o-mini' COMMENT '模型名称',
    temperature     DECIMAL(3,2)    NOT NULL DEFAULT 0.30 COMMENT '采样温度',
    max_tokens      INT             NOT NULL DEFAULT 2048 COMMENT '单次最大 token',
    enabled         TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用 1启用 0停用',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 模型接入配置表';

-- ---------------------------------------------------------------------
-- 2. 资产表(股票 / 基金)
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS asset;
CREATE TABLE asset (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    code            VARCHAR(32)     NOT NULL COMMENT '代码, 如 000001 / 510300',
    name            VARCHAR(100)    NOT NULL COMMENT '名称',
    type            VARCHAR(16)     NOT NULL COMMENT '类型 STOCK股票 / FUND基金',
    market          VARCHAR(32)     DEFAULT NULL COMMENT '市场, 如 SH/SZ/HK/US',
    latest_price    DECIMAL(18,4)   DEFAULT NULL COMMENT '最新价',
    prev_close      DECIMAL(18,4)   DEFAULT NULL COMMENT '昨收价',
    change_pct      DECIMAL(8,4)    DEFAULT NULL COMMENT '当日涨跌幅(%)',
    price_updated_at DATETIME       DEFAULT NULL COMMENT '价格更新时间',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code),
    KEY idx_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产(股票/基金)表';

-- ---------------------------------------------------------------------
-- 3. 持仓表
--    记录用户某个资产的当前持仓: 份额、平均成本、投入金额
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS holding;
CREATE TABLE holding (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    asset_id        BIGINT          NOT NULL COMMENT '资产ID',
    shares          DECIMAL(18,4)   NOT NULL DEFAULT 0 COMMENT '持有份额/股数',
    avg_cost        DECIMAL(18,4)   NOT NULL DEFAULT 0 COMMENT '平均成本价',
    invested_amount DECIMAL(18,2)   NOT NULL DEFAULT 0 COMMENT '累计净投入金额',
    realized_profit DECIMAL(18,2)   NOT NULL DEFAULT 0 COMMENT '已实现盈亏(卖出部分)',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_asset (asset_id),
    CONSTRAINT fk_holding_asset FOREIGN KEY (asset_id) REFERENCES asset (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户持仓表';

-- ---------------------------------------------------------------------
-- 4. 交易记录表(买入 / 卖出, 历史盈亏)
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS transaction_record;
CREATE TABLE transaction_record (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    asset_id        BIGINT          NOT NULL COMMENT '资产ID',
    type            VARCHAR(8)      NOT NULL COMMENT '类型 BUY买入 / SELL卖出',
    shares          DECIMAL(18,4)   NOT NULL COMMENT '成交份额/股数',
    price           DECIMAL(18,4)   NOT NULL COMMENT '成交价',
    amount          DECIMAL(18,2)   NOT NULL COMMENT '成交金额',
    fee             DECIMAL(18,2)   NOT NULL DEFAULT 0 COMMENT '手续费',
    realized_profit DECIMAL(18,2)   NOT NULL DEFAULT 0 COMMENT '本次卖出实现盈亏(买入为0)',
    trade_date      DATE            NOT NULL COMMENT '交易日期',
    note            VARCHAR(255)    DEFAULT NULL COMMENT '备注',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_asset (asset_id),
    KEY idx_trade_date (trade_date),
    CONSTRAINT fk_txn_asset FOREIGN KEY (asset_id) REFERENCES asset (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易记录表';

-- ---------------------------------------------------------------------
-- 5. 价格历史表(用于 AI 读取历史信息)
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS price_history;
CREATE TABLE price_history (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    asset_id        BIGINT          NOT NULL COMMENT '资产ID',
    trade_date      DATE            NOT NULL COMMENT '交易日期',
    open_price      DECIMAL(18,4)   DEFAULT NULL COMMENT '开盘价',
    close_price     DECIMAL(18,4)   DEFAULT NULL COMMENT '收盘价',
    high_price      DECIMAL(18,4)   DEFAULT NULL COMMENT '最高价',
    low_price       DECIMAL(18,4)   DEFAULT NULL COMMENT '最低价',
    volume          BIGINT          DEFAULT NULL COMMENT '成交量',
    change_pct      DECIMAL(8,4)    DEFAULT NULL COMMENT '涨跌幅(%)',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_asset_date (asset_id, trade_date),
    CONSTRAINT fk_price_asset FOREIGN KEY (asset_id) REFERENCES asset (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='价格历史表';

-- ---------------------------------------------------------------------
-- 6. 新闻表(爬虫抓取的最新相关新闻)
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS news_article;
CREATE TABLE news_article (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    asset_code      VARCHAR(32)     DEFAULT NULL COMMENT '关联资产代码',
    keyword         VARCHAR(100)    DEFAULT NULL COMMENT '检索关键词',
    title           VARCHAR(500)    NOT NULL COMMENT '标题',
    url             VARCHAR(1000)   DEFAULT NULL COMMENT '链接',
    source          VARCHAR(100)    DEFAULT NULL COMMENT '来源',
    summary         TEXT            DEFAULT NULL COMMENT '摘要',
    sentiment       VARCHAR(16)     DEFAULT NULL COMMENT '情绪 POSITIVE/NEGATIVE/NEUTRAL',
    published_at    DATETIME        DEFAULT NULL COMMENT '发布时间',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '抓取时间',
    PRIMARY KEY (id),
    KEY idx_asset_code (asset_code),
    KEY idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='爬取新闻表';

-- ---------------------------------------------------------------------
-- 7. AI 预测记录表(思维树 + 多轮验证 结果)
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS prediction;
CREATE TABLE prediction (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    asset_id        BIGINT          DEFAULT NULL COMMENT '资产ID',
    asset_code      VARCHAR(32)     NOT NULL COMMENT '资产代码',
    conclusion      VARCHAR(16)     NOT NULL COMMENT '结论 UP看涨/DOWN看跌/UNCERTAIN不确定',
    action          VARCHAR(16)     NOT NULL COMMENT '建议 INCREASE增持/DECREASE减持/HOLD观望',
    confidence      DECIMAL(5,2)    NOT NULL COMMENT '置信度(%)',
    rounds          INT             NOT NULL COMMENT '多轮验证总轮数',
    agree_count     INT             NOT NULL COMMENT '与最终结论一致的轮数',
    decomposition   TEXT            DEFAULT NULL COMMENT '思维链拆分(子问题)',
    thought_tree    LONGTEXT        DEFAULT NULL COMMENT '思维树过程(JSON)',
    reasoning       LONGTEXT        DEFAULT NULL COMMENT '最终推理说明',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_asset_code (asset_code),
    KEY idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 预测记录表';

-- ---------------------------------------------------------------------
-- 8. 理财规划记录表(思维树 + 多轮验证 结果)
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS finance_plan;
CREATE TABLE finance_plan (
    id                  BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    monthly_salary      DECIMAL(18,2) NOT NULL COMMENT '月工资',
    monthly_expense     DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '月支出',
    risk_style          VARCHAR(16) NOT NULL COMMENT '风格 CONSERVATIVE保守/BALANCED均衡/AGGRESSIVE激进',
    emergency_fund      DECIMAL(18,2) DEFAULT NULL COMMENT '建议应急金',
    investable_amount   DECIMAL(18,2) DEFAULT NULL COMMENT '建议可投资金额',
    confidence          DECIMAL(5,2) DEFAULT NULL COMMENT '方案置信度(%)',
    rounds              INT          DEFAULT NULL COMMENT '多轮验证轮数',
    decomposition       TEXT         DEFAULT NULL COMMENT '思维链拆分',
    thought_tree        LONGTEXT     DEFAULT NULL COMMENT '思维树过程(JSON)',
    plan_detail         LONGTEXT     DEFAULT NULL COMMENT '理财方案明细(JSON)',
    reasoning           LONGTEXT     DEFAULT NULL COMMENT '最终推理说明',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='理财规划记录表';

-- ---------------------------------------------------------------------
-- 9. 工资记录表(历史工资, 用于理财规划参考)
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS salary_record;
CREATE TABLE salary_record (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    month           VARCHAR(7)      NOT NULL COMMENT '月份 yyyy-MM',
    salary          DECIMAL(18,2)   NOT NULL COMMENT '工资收入',
    expense         DECIMAL(18,2)   NOT NULL DEFAULT 0 COMMENT '支出',
    saving          DECIMAL(18,2)   NOT NULL DEFAULT 0 COMMENT '结余',
    note            VARCHAR(255)    DEFAULT NULL COMMENT '备注',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_month (month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工资记录表';

-- ---------------------------------------------------------------------
-- 10. 用户财务档案表(单条记录, 维护可编辑的现金余额等)
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS user_profile;
CREATE TABLE user_profile (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    cash_balance    DECIMAL(18,2)   NOT NULL DEFAULT 0 COMMENT '现金余额',
    monthly_income  DECIMAL(18,2)   DEFAULT NULL COMMENT '月收入(可选, 覆盖工资记录)',
    monthly_expense DECIMAL(18,2)   DEFAULT NULL COMMENT '月支出(可选)',
    risk_preference VARCHAR(16)     DEFAULT NULL COMMENT '风险偏好 CONSERVATIVE/BALANCED/AGGRESSIVE',
    note            VARCHAR(500)    DEFAULT NULL COMMENT '备注',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户财务档案表';

-- ---------------------------------------------------------------------
-- 11. 理财分析报告表(一键汇总 / 建议结果)
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS advisor_report;
CREATE TABLE advisor_report (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    type            VARCHAR(16)     NOT NULL COMMENT '类型 SUMMARY汇总 / ADVICE建议',
    intention       VARCHAR(1000)   DEFAULT NULL COMMENT '用户理财意向(ADVICE)',
    metrics         LONGTEXT        DEFAULT NULL COMMENT '关键财务指标(JSON)',
    content         LONGTEXT        DEFAULT NULL COMMENT 'AI 分析结果(JSON)',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_type (type),
    KEY idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='理财分析报告表';

-- ---------------------------------------------------------------------
-- 12. RAG 知识文档表(历史理财情况 + 核心股票/基金波动)
--     作为检索增强(RAG)的源文档, 向量索引在应用内存中重建
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS rag_document;
CREATE TABLE rag_document (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    doc_type        VARCHAR(32)     NOT NULL COMMENT '文档类型 ASSET_VOLATILITY/HOLDING_PNL/FINANCE_OVERVIEW/PREDICTION_HISTORY/FINANCE_PLAN/SALARY',
    ref_code        VARCHAR(64)     DEFAULT NULL COMMENT '关联代码(资产代码等)',
    title           VARCHAR(255)    DEFAULT NULL COMMENT '标题',
    content         LONGTEXT        NOT NULL COMMENT '文档内容(被向量化的文本)',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_doc_type (doc_type),
    KEY idx_ref_code (ref_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG 知识文档表';

-- =====================================================================
-- 初始化示例数据(可选)
-- =====================================================================
INSERT INTO user_profile (cash_balance, monthly_income, monthly_expense, risk_preference, note)
VALUES (50000.00, 20000.00, 8000.00, 'BALANCED', '示例财务档案');
INSERT INTO asset (code, name, type, market, latest_price, prev_close, change_pct)
VALUES
    ('510300', '沪深300ETF', 'FUND', 'SH', 3.8520, 3.8210, 0.8113),
    ('000001', '平安银行', 'STOCK', 'SZ', 11.2300, 11.0500, 1.6290),
    ('161725', '招商中证白酒指数', 'FUND', 'SZ', 0.9870, 0.9950, -0.8040);
