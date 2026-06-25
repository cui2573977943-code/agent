package com.aifinance.service.ai.tot;

import com.aifinance.config.AgentProperties;
import com.aifinance.entity.AiConfig;
import com.aifinance.service.ai.AiClient;
import com.aifinance.service.ai.ChatMessage;
import com.aifinance.service.ai.JsonExtractor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单只股票/基金涨跌预测的 思维树(Tree of Thought) Agent。
 *
 * <p>流程:
 * <ol>
 *   <li>思维链拆分(CoT): 让模型把"预测涨跌"拆分为多个分析维度;</li>
 *   <li>思维树展开(ToT): 对每个维度生成多个候选思路(分支)并打分, 结合历史信息与最新爬虫新闻;</li>
 *   <li>多轮判断: 基于完整的思维树做 N 轮独立裁决, 每轮给出 UP/DOWN 与置信度;</li>
 *   <li>聚合: 多数派占比 &gt; 阈值(默认 60%) 才确认为最终结论, 否则判为 UNCERTAIN;</li>
 *   <li>映射操作建议: UP→增持, DOWN→减持, UNCERTAIN→观望(结合当前是否持仓)。</li>
 * </ol>
 */
@Component
public class PredictionAgent {

    private static final Logger log = LoggerFactory.getLogger(PredictionAgent.class);

    private final AiClient aiClient;
    private final ObjectMapper objectMapper;
    private final AgentProperties properties;

    public PredictionAgent(AiClient aiClient, ObjectMapper objectMapper, AgentProperties properties) {
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * 执行一次完整的思维树预测。
     *
     * @param config         AI 配置
     * @param assetName      资产名称
     * @param assetCode      资产代码
     * @param historySummary 历史价格/持仓信息摘要
     * @param newsSummary    最新新闻摘要
     * @param holdingSummary 当前持仓与盈亏摘要
     */
    public PredictionOutcome run(AiConfig config, String assetName, String assetCode,
                                 String historySummary, String newsSummary, String holdingSummary) {
        AgentProperties.Prediction cfg = properties.getPrediction();
        PredictionOutcome outcome = new PredictionOutcome();

        // 1) 思维链拆分
        List<String> dimensions = decompose(config, assetName, assetCode);
        outcome.setDecomposition(dimensions);

        // 2) 思维树展开
        ThoughtNode root = buildThoughtTree(config, assetName, assetCode,
                historySummary, newsSummary, dimensions, cfg.getBranches());
        outcome.setThoughtTree(root);

        // 3) 多轮判断
        String treeText = renderTree(root);
        List<VoteResult> votes = multiRoundVote(config, assetName, assetCode,
                historySummary, newsSummary, holdingSummary, treeText, cfg.getRounds());
        outcome.setVotes(votes);
        outcome.setRounds(votes.size());

        // 4) 聚合多数派与置信度
        aggregate(outcome, cfg.getConfidenceThreshold());

        // 5) 映射操作建议 + 生成总结
        mapAction(outcome, holdingSummary);
        outcome.setReasoning(summarize(config, assetName, assetCode, outcome, treeText));
        return outcome;
    }

    // ---------------- 步骤 1: 思维链拆分 ----------------

    private List<String> decompose(AiConfig config, String assetName, String assetCode) {
        String sys = "你是顶级量化投资分析师。请使用思维链(Chain-of-Thought)方法, "
                + "把'预测某标的短期涨跌'这个任务拆分为 4-6 个关键分析维度。"
                + "只输出 JSON 数组, 每个元素是一个维度名称字符串, 不要输出其他文字。";
        String user = String.format("标的: %s(%s)。请给出分析维度 JSON 数组。", assetName, assetCode);

        String resp = aiClient.chat(config, List.of(ChatMessage.system(sys), ChatMessage.user(user)));
        JsonNode node = JsonExtractor.extract(objectMapper, resp);
        List<String> dims = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(n -> {
                String s = n.isTextual() ? n.asText() : n.path("name").asText("");
                if (!s.isBlank()) dims.add(s.trim());
            });
        }
        if (dims.isEmpty()) {
            dims.add("历史价格与趋势");
            dims.add("最新新闻与市场情绪");
            dims.add("宏观与行业环境");
            dims.add("资金面与成交量");
            dims.add("风险与不确定性");
        }
        return dims;
    }

    // ---------------- 步骤 2: 思维树展开 ----------------

    private ThoughtNode buildThoughtTree(AiConfig config, String assetName, String assetCode,
                                         String historySummary, String newsSummary,
                                         List<String> dimensions, int branches) {
        ThoughtNode root = new ThoughtNode("ROOT", "预测 " + assetName + "(" + assetCode + ") 短期涨跌");

        String sys = "你是资深投资分析师, 正在构建思维树(Tree-of-Thought)。"
                + "针对给定的每一个分析维度, 生成 " + branches + " 条不同的候选思路(分支), "
                + "每条思路需基于提供的历史信息与最新新闻, 给出: 倾向(stance: UP/DOWN/NEUTRAL)、"
                + "0~1 之间的可信度评分(score)、以及简短分析(content)。"
                + "严格只输出 JSON 数组, 元素结构为 {\"stance\":\"\",\"score\":0.0,\"content\":\"\"}。";

        for (String dim : dimensions) {
            ThoughtNode dimNode = new ThoughtNode("DIMENSION", dim);
            String user = "标的: " + assetName + "(" + assetCode + ")\n"
                    + "分析维度: " + dim + "\n"
                    + "【历史信息】\n" + safe(historySummary) + "\n"
                    + "【最新新闻】\n" + safe(newsSummary) + "\n"
                    + "请生成该维度下的 " + branches + " 条候选思路 JSON 数组。";
            try {
                String resp = aiClient.chat(config,
                        List.of(ChatMessage.system(sys), ChatMessage.user(user)));
                JsonNode arr = JsonExtractor.extract(objectMapper, resp);
                if (arr != null && arr.isArray()) {
                    arr.forEach(n -> {
                        ThoughtNode t = new ThoughtNode("THOUGHT", dim + " - 候选思路");
                        t.setStance(n.path("stance").asText("NEUTRAL"));
                        t.setScore(n.path("score").asDouble(0.5));
                        t.setContent(n.path("content").asText(""));
                        dimNode.addChild(t);
                    });
                }
            } catch (Exception e) {
                log.warn("维度 [{}] 思维树展开失败: {}", dim, e.getMessage());
            }
            if (dimNode.getChildren().isEmpty()) {
                ThoughtNode t = new ThoughtNode("THOUGHT", dim + " - 候选思路");
                t.setStance("NEUTRAL");
                t.setScore(0.5);
                t.setContent("该维度暂无足够信息支撑明确判断。");
                dimNode.addChild(t);
            }
            root.addChild(dimNode);
        }
        return root;
    }

    // ---------------- 步骤 3: 多轮判断 ----------------

    private List<VoteResult> multiRoundVote(AiConfig config, String assetName, String assetCode,
                                            String historySummary, String newsSummary,
                                            String holdingSummary, String treeText, int rounds) {
        List<VoteResult> votes = new ArrayList<>();
        String sys = "你是严谨的投资决策评审。基于给定的思维树、历史信息、最新新闻与持仓情况, "
                + "对标的短期(未来 1~4 周)走势做出独立裁决。"
                + "只输出 JSON: {\"verdict\":\"UP|DOWN\",\"confidence\":0~1,\"reason\":\"...\"}。"
                + "verdict 只能是 UP 或 DOWN, 不允许中立。";

        for (int i = 1; i <= rounds; i++) {
            String user = "第 " + i + " 轮独立裁决。\n"
                    + "标的: " + assetName + "(" + assetCode + ")\n"
                    + "【思维树】\n" + treeText + "\n"
                    + "【历史信息】\n" + safe(historySummary) + "\n"
                    + "【最新新闻】\n" + safe(newsSummary) + "\n"
                    + "【当前持仓与盈亏】\n" + safe(holdingSummary) + "\n"
                    + "请给出本轮裁决 JSON。";
            VoteResult v = new VoteResult();
            v.setRound(i);
            try {
                String resp = aiClient.chat(config,
                        List.of(ChatMessage.system(sys), ChatMessage.user(user)));
                JsonNode node = JsonExtractor.extract(objectMapper, resp);
                if (node != null) {
                    String verdict = node.path("verdict").asText("").trim().toUpperCase();
                    if (!verdict.equals("UP") && !verdict.equals("DOWN")) {
                        verdict = "DOWN".equalsIgnoreCase(verdict) ? "DOWN" : "UP";
                    }
                    v.setVerdict(verdict);
                    v.setConfidence(clamp01(node.path("confidence").asDouble(0.5)));
                    v.setReason(node.path("reason").asText(""));
                } else {
                    v.setVerdict("UP");
                    v.setConfidence(0.5);
                    v.setReason("解析失败, 默认中性偏多。");
                }
            } catch (Exception e) {
                log.warn("第 {} 轮裁决失败: {}", i, e.getMessage());
                v.setVerdict("UP");
                v.setConfidence(0.5);
                v.setReason("调用异常: " + e.getMessage());
            }
            votes.add(v);
        }
        return votes;
    }

    // ---------------- 步骤 4: 聚合 ----------------

    private void aggregate(PredictionOutcome outcome, double threshold) {
        int up = 0, down = 0;
        for (VoteResult v : outcome.getVotes()) {
            if ("UP".equals(v.getVerdict())) up++;
            else if ("DOWN".equals(v.getVerdict())) down++;
        }
        int total = outcome.getVotes().size();
        int majority = Math.max(up, down);
        String label = up >= down ? "UP" : "DOWN";
        double ratio = total == 0 ? 0 : (double) majority / total;

        outcome.setAgreeCount(majority);
        if (ratio >= threshold) {
            outcome.setConclusion(label);
            outcome.setConfirmed(true);
        } else {
            outcome.setConclusion("UNCERTAIN");
            outcome.setConfirmed(false);
        }
        outcome.setConfidence(round2(ratio * 100));
    }

    // ---------------- 步骤 5: 操作建议 + 总结 ----------------

    private void mapAction(PredictionOutcome outcome, String holdingSummary) {
        switch (outcome.getConclusion()) {
            case "UP" -> outcome.setAction("INCREASE");
            case "DOWN" -> outcome.setAction("DECREASE");
            default -> outcome.setAction("HOLD");
        }
    }

    private String summarize(AiConfig config, String assetName, String assetCode,
                             PredictionOutcome outcome, String treeText) {
        try {
            String sys = "你是投资顾问。请用简洁中文总结对该标的的最终判断与操作建议, "
                    + "说明依据与主要风险, 200 字以内。";
            String user = "标的: " + assetName + "(" + assetCode + ")\n"
                    + "最终结论: " + outcome.getConclusion()
                    + " (" + (outcome.isConfirmed() ? "已确认" : "未达确认阈值") + ")\n"
                    + "置信度: " + outcome.getConfidence() + "%\n"
                    + "操作建议: " + outcome.getAction() + "\n"
                    + "【思维树】\n" + treeText;
            return aiClient.chat(config, List.of(ChatMessage.system(sys), ChatMessage.user(user)));
        } catch (Exception e) {
            return "结论: " + outcome.getConclusion() + ", 建议: " + outcome.getAction()
                    + ", 置信度: " + outcome.getConfidence() + "%。(总结生成失败: " + e.getMessage() + ")";
        }
    }

    // ---------------- 工具方法 ----------------

    private String renderTree(ThoughtNode root) {
        StringBuilder sb = new StringBuilder();
        sb.append("根: ").append(root.getTitle()).append("\n");
        for (ThoughtNode dim : root.getChildren()) {
            sb.append("● 维度: ").append(dim.getTitle()).append("\n");
            int idx = 1;
            for (ThoughtNode t : dim.getChildren()) {
                sb.append("   ").append(idx++).append(") [")
                        .append(t.getStance()).append(", score=").append(t.getScore())
                        .append("] ").append(t.getContent()).append("\n");
            }
        }
        return sb.toString();
    }

    public Map<String, Object> treeToMap(ThoughtNode node) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", node.getType());
        m.put("title", node.getTitle());
        if (node.getStance() != null) m.put("stance", node.getStance());
        if (node.getScore() != null) m.put("score", node.getScore());
        if (node.getContent() != null) m.put("content", node.getContent());
        if (!node.getChildren().isEmpty()) {
            List<Map<String, Object>> kids = new ArrayList<>();
            node.getChildren().forEach(c -> kids.add(treeToMap(c)));
            m.put("children", kids);
        }
        return m;
    }

    private String safe(String s) {
        return (s == null || s.isBlank()) ? "(无)" : s;
    }

    private double clamp01(double v) {
        if (v < 0) return 0;
        return Math.min(v, 1);
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
