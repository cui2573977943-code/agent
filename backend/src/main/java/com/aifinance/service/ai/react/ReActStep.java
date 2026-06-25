package com.aifinance.service.ai.react;

import lombok.Data;

/**
 * ReAct 单步: 思考(Thought) -> 行动(Action/ActionInput) -> 观察(Observation)。
 */
@Data
public class ReActStep {
    private int step;
    private String thought;
    private String action;
    private String actionInput;
    private String observation;
}
