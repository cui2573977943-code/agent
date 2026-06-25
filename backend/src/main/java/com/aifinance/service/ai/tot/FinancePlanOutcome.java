package com.aifinance.service.ai.tot;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 理财规划 Agent 的产出。
 */
@Data
public class FinancePlanOutcome {

    /** 风险风格: CONSERVATIVE / BALANCED / AGGRESSIVE */
    private String riskStyle;

    /** 建议应急金 */
    private double emergencyFund;

    /** 建议可投资金额 */
    private double investableAmount;

    /** 置信度(%) */
    private double confidence;

    private int rounds;

    private int agreeCount;

    private boolean confirmed;

    /** 思维链拆分维度 */
    private List<String> decomposition = new ArrayList<>();

    /** 思维树根节点 */
    private ThoughtNode thoughtTree;

    /** 每轮投票 */
    private List<VoteResult> votes = new ArrayList<>();

    /**
     * 具体配置建议条目, 每条形如:
     * {category, target, action(BUY/INCREASE/DECREASE/HOLD), amount, ratio, reason}
     */
    private List<Map<String, Object>> allocations = new ArrayList<>();

    /** 最终文字说明 */
    private String reasoning;
}
