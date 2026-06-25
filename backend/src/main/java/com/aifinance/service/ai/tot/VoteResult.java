package com.aifinance.service.ai.tot;

import lombok.Data;

/**
 * 单轮验证投票结果。
 */
@Data
public class VoteResult {

    private int round;
    /** 该轮结论标签, 如 UP/DOWN 或 CONSERVATIVE/BALANCED/AGGRESSIVE */
    private String verdict;
    /** 该轮模型自评置信度(0-1) */
    private double confidence;
    private String reason;
}
