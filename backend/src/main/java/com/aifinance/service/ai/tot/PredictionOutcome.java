package com.aifinance.service.ai.tot;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 预测 Agent 的产出。
 */
@Data
public class PredictionOutcome {

    /** 最终结论: UP / DOWN / UNCERTAIN */
    private String conclusion;

    /** 操作建议: INCREASE / DECREASE / HOLD */
    private String action;

    /** 置信度(%), 即多数派票数占比 */
    private double confidence;

    /** 多轮验证总轮数 */
    private int rounds;

    /** 与最终结论一致的轮数 */
    private int agreeCount;

    /** 是否达到确认阈值(>60%) */
    private boolean confirmed;

    /** 思维链拆分出的维度 */
    private List<String> decomposition = new ArrayList<>();

    /** 思维树(根节点) */
    private ThoughtNode thoughtTree;

    /** 每轮投票明细 */
    private List<VoteResult> votes = new ArrayList<>();

    /** RAG 检索到的相关资料(历史理财 + 核心波动) */
    private List<Map<String, Object>> ragSnippets = new ArrayList<>();

    /** 最终推理说明 */
    private String reasoning;
}
