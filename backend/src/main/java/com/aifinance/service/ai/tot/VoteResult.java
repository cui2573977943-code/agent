package com.aifinance.service.ai.tot;

import com.aifinance.service.ai.react.ReActStep;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

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
    /** 该轮 ReAct 推理轨迹(若使用 ReAct 模式) */
    private List<ReActStep> reactSteps = new ArrayList<>();
}
