package com.aifinance.service.ai.react;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次 ReAct 推理(一个 episode)的结果。
 */
@Data
public class ReActResult {
    /** 最终答案(通常为一段 JSON 文本, 由调用方解析) */
    private String finalAnswer;
    /** 推理轨迹 */
    private List<ReActStep> steps = new ArrayList<>();
    /** 是否正常给出 FINAL */
    private boolean success;
}
