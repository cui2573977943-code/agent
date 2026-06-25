package com.aifinance.service.ai.tot;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 思维树节点。
 * <p>
 * 结构: 根节点(任务) -> 思维链拆分出的若干维度节点 -> 每个维度下的若干候选思路(分支)。
 * 多轮验证的投票结果挂在根节点的 votes 上。
 */
@Data
public class ThoughtNode {

    /** 节点类型: ROOT / DIMENSION / THOUGHT / VERDICT */
    private String type;

    /** 节点标题/维度名 */
    private String title;

    /** 节点详细内容/分析 */
    private String content;

    /** 倾向: UP/DOWN/NEUTRAL 或 CONSERVATIVE/AGGRESSIVE 等 */
    private String stance;

    /** 该思路的评分(0-1), 表示其可信度/重要性 */
    private Double score;

    /** 子节点 */
    private List<ThoughtNode> children = new ArrayList<>();

    public ThoughtNode() {
    }

    public ThoughtNode(String type, String title) {
        this.type = type;
        this.title = title;
    }

    public void addChild(ThoughtNode child) {
        this.children.add(child);
    }
}
