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
 * 工资理财规划的 思维树(Tree of Thought) Agent。
 *
 * <p>流程:
 * <ol>
 *   <li>思维链拆分(CoT): 把"如何理财"拆为应急金、消费、风险偏好、资产配置等维度;</li>
 *   <li>思维树展开(ToT): 围绕维度生成保守 / 均衡 / 激进 等候选策略分支并打分,
 *       结合历史理财盈亏结果;</li>
 *   <li>多轮验证: N 轮独立裁决, 在 CONSERVATIVE / BALANCED / AGGRESSIVE 中投票;</li>
 *   <li>聚合: 多数派占比 &gt; 阈值才确认, 据此确定风格(历史盈→可偏激进, 历史亏→偏保守);</li>
 *   <li>生成具体方案: 应急金、可投资金额、买哪个基金、原有持仓增持/减持。</li>
 * </ol>
 */
@Component
public class FinancePlanAgent {

    private static final Logger log = LoggerFactory.getLogger(FinancePlanAgent.class);

    private final AiClient aiClient;
    private final ObjectMapper objectMapper;
    private final AgentProperties properties;

    public FinancePlanAgent(AiClient aiClient, ObjectMapper objectMapper, AgentProperties properties) {
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * 执行一次完整的理财规划。
     *
     * @param config          AI 配置
     * @param salary          月工资
     * @param expense         月支出
     * @param historyPnl      历史理财盈亏摘要
     * @param holdingsSummary 当前持仓摘要
     * @param candidateAssets 可选基金/股票清单摘要
     */
    public FinancePlanOutcome run(AiConfig config, double salary, double expense,
                                  String historyPnl, String holdingsSummary, String candidateAssets) {
        AgentProperties.Finance cfg = properties.getFinance();
        FinancePlanOutcome outcome = new FinancePlanOutcome();

        // 1) 思维链拆分
        List<String> dimensions = decompose(config, salary, expense);
        outcome.setDecomposition(dimensions);

        // 2) 思维树展开(候选策略)
        ThoughtNode root = buildThoughtTree(config, salary, expense, historyPnl,
                holdingsSummary, dimensions);
        outcome.setThoughtTree(root);
        String treeText = renderTree(root);

        // 3) 多轮验证: 决定风格
        List<VoteResult> votes = multiRoundVote(config, salary, expense, historyPnl,
                holdingsSummary, treeText, cfg.getRounds());
        outcome.setVotes(votes);
        outcome.setRounds(votes.size());

        // 4) 聚合风格
        aggregateStyle(outcome, cfg.getConfidenceThreshold());

        // 5) 生成具体方案
        generatePlan(config, outcome, salary, expense, historyPnl, holdingsSummary, candidateAssets, treeText);
        return outcome;
    }

    // ---------------- 1. 思维链拆分 ----------------

    private List<String> decompose(AiConfig config, double salary, double expense) {
        String sys = "你是专业理财规划师(CFP)。请用思维链方法, 把'如何规划月工资进行理财'拆分为 "
                + "4-6 个关键维度。只输出 JSON 数组(维度名称字符串), 不要其他文字。";
        String user = "月工资: " + salary + " 元, 月支出: " + expense + " 元。请给出维度 JSON 数组。";
        try {
            String resp = aiClient.chat(config, List.of(ChatMessage.system(sys), ChatMessage.user(user)));
            JsonNode node = JsonExtractor.extract(objectMapper, resp);
            List<String> dims = new ArrayList<>();
            if (node != null && node.isArray()) {
                node.forEach(n -> {
                    String s = n.isTextual() ? n.asText() : n.path("name").asText("");
                    if (!s.isBlank()) dims.add(s.trim());
                });
            }
            if (!dims.isEmpty()) return dims;
        } catch (Exception e) {
            log.warn("理财思维链拆分失败: {}", e.getMessage());
        }
        return new ArrayList<>(List.of(
                "应急储备金", "日常与负债管理", "风险偏好评估",
                "资产配置组合", "定投与再平衡", "目标与流动性"));
    }

    // ---------------- 2. 思维树展开 ----------------

    private ThoughtNode buildThoughtTree(AiConfig config, double salary, double expense,
                                         String historyPnl, String holdingsSummary,
                                         List<String> dimensions) {
        ThoughtNode root = new ThoughtNode("ROOT", "月工资理财规划");
        String sys = "你是理财规划师, 正在构建思维树。针对每个维度, 生成 3 条候选策略分支, "
                + "每条给出: 风格倾向(stance: CONSERVATIVE/BALANCED/AGGRESSIVE)、0~1 评分(score)、"
                + "简短说明(content)。需结合历史理财盈亏(盈利可更激进, 亏损应更保守)。"
                + "严格只输出 JSON 数组, 元素为 {\"stance\":\"\",\"score\":0.0,\"content\":\"\"}。";

        for (String dim : dimensions) {
            ThoughtNode dimNode = new ThoughtNode("DIMENSION", dim);
            String user = "维度: " + dim + "\n"
                    + "月工资: " + salary + ", 月支出: " + expense + "\n"
                    + "【历史理财盈亏】\n" + safe(historyPnl) + "\n"
                    + "【当前持仓】\n" + safe(holdingsSummary) + "\n"
                    + "请生成该维度下 3 条候选策略 JSON 数组。";
            try {
                String resp = aiClient.chat(config, List.of(ChatMessage.system(sys), ChatMessage.user(user)));
                JsonNode arr = JsonExtractor.extract(objectMapper, resp);
                if (arr != null && arr.isArray()) {
                    arr.forEach(n -> {
                        ThoughtNode t = new ThoughtNode("THOUGHT", dim + " - 候选策略");
                        t.setStance(n.path("stance").asText("BALANCED"));
                        t.setScore(n.path("score").asDouble(0.5));
                        t.setContent(n.path("content").asText(""));
                        dimNode.addChild(t);
                    });
                }
            } catch (Exception e) {
                log.warn("理财维度 [{}] 展开失败: {}", dim, e.getMessage());
            }
            if (dimNode.getChildren().isEmpty()) {
                ThoughtNode t = new ThoughtNode("THOUGHT", dim + " - 候选策略");
                t.setStance("BALANCED");
                t.setScore(0.5);
                t.setContent("信息不足, 采用均衡默认策略。");
                dimNode.addChild(t);
            }
            root.addChild(dimNode);
        }
        return root;
    }

    // ---------------- 3. 多轮验证 ----------------

    private List<VoteResult> multiRoundVote(AiConfig config, double salary, double expense,
                                            String historyPnl, String holdingsSummary,
                                            String treeText, int rounds) {
        List<VoteResult> votes = new ArrayList<>();
        String sys = "你是理财评审专家。基于思维树、历史理财盈亏与现状, 独立裁决整体理财风格。"
                + "只输出 JSON: {\"verdict\":\"CONSERVATIVE|BALANCED|AGGRESSIVE\",\"confidence\":0~1,\"reason\":\"...\"}。";
        for (int i = 1; i <= rounds; i++) {
            String user = "第 " + i + " 轮独立裁决。\n"
                    + "月工资: " + salary + ", 月支出: " + expense + "\n"
                    + "【思维树】\n" + treeText + "\n"
                    + "【历史理财盈亏】\n" + safe(historyPnl) + "\n"
                    + "【当前持仓】\n" + safe(holdingsSummary) + "\n"
                    + "请给出本轮风格裁决 JSON。";
            VoteResult v = new VoteResult();
            v.setRound(i);
            try {
                String resp = aiClient.chat(config, List.of(ChatMessage.system(sys), ChatMessage.user(user)));
                JsonNode node = JsonExtractor.extract(objectMapper, resp);
                if (node != null) {
                    String verdict = normalizeStyle(node.path("verdict").asText("BALANCED"));
                    v.setVerdict(verdict);
                    v.setConfidence(clamp01(node.path("confidence").asDouble(0.5)));
                    v.setReason(node.path("reason").asText(""));
                } else {
                    v.setVerdict("BALANCED");
                    v.setConfidence(0.5);
                    v.setReason("解析失败, 默认均衡。");
                }
            } catch (Exception e) {
                log.warn("理财第 {} 轮裁决失败: {}", i, e.getMessage());
                v.setVerdict("BALANCED");
                v.setConfidence(0.5);
                v.setReason("调用异常: " + e.getMessage());
            }
            votes.add(v);
        }
        return votes;
    }

    // ---------------- 4. 聚合风格 ----------------

    private void aggregateStyle(FinancePlanOutcome outcome, double threshold) {
        Map<String, Integer> counter = new LinkedHashMap<>();
        counter.put("CONSERVATIVE", 0);
        counter.put("BALANCED", 0);
        counter.put("AGGRESSIVE", 0);
        for (VoteResult v : outcome.getVotes()) {
            counter.merge(v.getVerdict(), 1, Integer::sum);
        }
        String best = "BALANCED";
        int max = -1;
        for (Map.Entry<String, Integer> e : counter.entrySet()) {
            if (e.getValue() > max) {
                max = e.getValue();
                best = e.getKey();
            }
        }
        int total = outcome.getVotes().size();
        double ratio = total == 0 ? 0 : (double) max / total;
        outcome.setAgreeCount(max);
        outcome.setRiskStyle(best);
        outcome.setConfirmed(ratio >= threshold);
        outcome.setConfidence(round2(ratio * 100));
    }

    // ---------------- 5. 生成具体方案 ----------------

    private void generatePlan(AiConfig config, FinancePlanOutcome outcome, double salary, double expense,
                              String historyPnl, String holdingsSummary, String candidateAssets,
                              String treeText) {
        double surplus = Math.max(salary - expense, 0);
        String sys = "你是理财规划师。请基于已确定的风格给出落地方案。"
                + "结合可选基金清单与当前持仓, 明确: 应急金(emergencyFund)、可投资金额(investableAmount), "
                + "以及 allocations 数组, 每条 {category, target, action(BUY新买/INCREASE增持/DECREASE减持/HOLD持有), amount金额, ratio占比0~1, reason}。"
                + "原有持仓要明确给出增持或减持。严格只输出 JSON: "
                + "{\"emergencyFund\":0,\"investableAmount\":0,\"allocations\":[...],\"summary\":\"...\"}。";
        String user = "已确定风格: " + outcome.getRiskStyle() + "\n"
                + "月工资: " + salary + ", 月支出: " + expense + ", 月结余约: " + surplus + "\n"
                + "【历史理财盈亏】\n" + safe(historyPnl) + "\n"
                + "【当前持仓】\n" + safe(holdingsSummary) + "\n"
                + "【可选基金/股票】\n" + safe(candidateAssets) + "\n"
                + "【思维树】\n" + treeText + "\n"
                + "请输出方案 JSON。";
        try {
            String resp = aiClient.chat(config, List.of(ChatMessage.system(sys), ChatMessage.user(user)));
            JsonNode node = JsonExtractor.extract(objectMapper, resp);
            if (node != null) {
                outcome.setEmergencyFund(node.path("emergencyFund").asDouble(surplus * 6));
                outcome.setInvestableAmount(node.path("investableAmount").asDouble(surplus * 0.5));
                List<Map<String, Object>> allocations = new ArrayList<>();
                JsonNode arr = node.path("allocations");
                if (arr.isArray()) {
                    arr.forEach(a -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("category", a.path("category").asText(""));
                        m.put("target", a.path("target").asText(""));
                        m.put("action", a.path("action").asText("HOLD"));
                        m.put("amount", a.path("amount").asDouble(0));
                        m.put("ratio", a.path("ratio").asDouble(0));
                        m.put("reason", a.path("reason").asText(""));
                        allocations.add(m);
                    });
                }
                outcome.setAllocations(allocations);
                outcome.setReasoning(node.path("summary").asText(""));
                return;
            }
        } catch (Exception e) {
            log.warn("理财方案生成失败: {}", e.getMessage());
        }
        // 降级默认方案
        outcome.setEmergencyFund(round2(surplus * 6));
        outcome.setInvestableAmount(round2(surplus * 0.5));
        outcome.setReasoning("方案生成失败, 采用默认建议: 预留 6 个月结余作为应急金, 结余的 50% 用于稳健投资。");
    }

    // ---------------- 工具 ----------------

    private String normalizeStyle(String s) {
        if (s == null) return "BALANCED";
        String u = s.trim().toUpperCase();
        if (u.contains("CONSERV") || u.contains("保守")) return "CONSERVATIVE";
        if (u.contains("AGGRESS") || u.contains("激进")) return "AGGRESSIVE";
        return "BALANCED";
    }

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
