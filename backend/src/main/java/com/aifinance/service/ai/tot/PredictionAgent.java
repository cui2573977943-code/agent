package com.aifinance.service.ai.tot;

import com.aifinance.config.AgentProperties;
import com.aifinance.entity.AiConfig;
import com.aifinance.service.ai.AiClient;
import com.aifinance.service.ai.ChatMessage;
import com.aifinance.service.ai.JsonExtractor;
import com.aifinance.service.ai.react.ReActEngine;
import com.aifinance.service.ai.react.ReActResult;
import com.aifinance.service.crawler.NewsCrawlerService;
import com.aifinance.service.rag.RagService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 单只股票/基金涨跌预测的 思维树(ToT) + RAG + ReAct Agent。
 *
 * <p>流程:
 * <ol>
 *   <li>RAG 检索: 先从知识库取回该标的的历史理财与核心波动资料;</li>
 *   <li>思维链拆分(CoT): 让模型把"预测涨跌"拆分为多个分析维度;</li>
 *   <li>思维树展开(ToT): 对每个维度生成多个候选思路(分支)并打分, 结合 RAG 资料/历史/新闻;</li>
 *   <li>多轮 ReAct 判断: 每轮以"思考-行动-观察"循环可调用 RAG/新闻工具收集证据后裁决 UP/DOWN;</li>
 *   <li>投票聚合: 多数派占比 &gt; 阈值(默认 60%) 才确认结论, 否则判为 UNCERTAIN;</li>
 *   <li>映射操作建议: UP→增持, DOWN→减持, UNCERTAIN→观望。</li>
 * </ol>
 */
@Component
public class PredictionAgent {

    private static final Logger log = LoggerFactory.getLogger(PredictionAgent.class);

    private final AiClient aiClient;
    private final ObjectMapper objectMapper;
    private final AgentProperties properties;
    private final RagService ragService;
    private final NewsCrawlerService newsCrawlerService;
    private final ReActEngine reActEngine;

    public PredictionAgent(AiClient aiClient, ObjectMapper objectMapper, AgentProperties properties,
                           RagService ragService, NewsCrawlerService newsCrawlerService,
                           ReActEngine reActEngine) {
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.ragService = ragService;
        this.newsCrawlerService = newsCrawlerService;
        this.reActEngine = reActEngine;
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

        // 0) RAG 检索: 先从知识库取回该标的相关的历史理财与核心波动资料
        String ragQuery = "预测 " + assetName + "(" + assetCode + ") 短期涨跌; 历史波动 盈亏 风险";
        List<Map<String, Object>> ragSnippets = safeRetrieve(ragQuery, 6);
        outcome.setRagSnippets(ragSnippets);
        String ragContext = formatRag(ragSnippets);

        // 1) 思维链拆分
        List<String> dimensions = decompose(config, assetName, assetCode);
        outcome.setDecomposition(dimensions);

        // 2) 思维树展开(注入 RAG 资料)
        ThoughtNode root = buildThoughtTree(config, assetName, assetCode,
                historySummary + "\n【RAG 检索资料】\n" + ragContext,
                newsSummary, dimensions, cfg.getBranches());
        outcome.setThoughtTree(root);

        // 3) 多轮 ReAct 判断
        String treeText = renderTree(root);
        List<VoteResult> votes = multiRoundReAct(config, assetName, assetCode,
                historySummary, newsSummary, holdingSummary, ragContext, treeText, cfg.getRounds());
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

        List<String> dims = new ArrayList<>();
        try {
            String resp = aiClient.chat(config, List.of(ChatMessage.system(sys), ChatMessage.user(user)));
            JsonNode node = JsonExtractor.extract(objectMapper, resp);
            if (node != null && node.isArray()) {
                node.forEach(n -> {
                    String s = n.isTextual() ? n.asText() : n.path("name").asText("");
                    if (!s.isBlank()) dims.add(s.trim());
                });
            }
        } catch (Exception e) {
            log.warn("预测思维链拆分失败, 使用默认维度: {}", e.getMessage());
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

    // ---------------- 步骤 3: 多轮 ReAct 判断 ----------------

    private List<VoteResult> multiRoundReAct(AiConfig config, String assetName, String assetCode,
                                             String historySummary, String newsSummary,
                                             String holdingSummary, String ragContext,
                                             String treeText, int rounds) {
        List<VoteResult> votes = new ArrayList<>();

        // ReAct 可用工具: 从 RAG 知识库检索, 以及检索最新新闻
        Map<String, Function<String, String>> tools = new LinkedHashMap<>();
        tools.put("RAG_SEARCH", input -> ragService.retrieveAsContext(
                (input == null || input.isBlank()) ? (assetName + " " + assetCode) : input, 5));
        tools.put("NEWS_SEARCH", input -> {
            String kw = (input == null || input.isBlank()) ? assetName : input;
            var news = newsCrawlerService.fetchAndStore(assetCode, kw);
            if (news.isEmpty()) return "(未检索到相关新闻)";
            StringBuilder sb = new StringBuilder();
            news.stream().limit(5).forEach(n -> sb.append("- ").append(n.getTitle())
                    .append(n.getSummary() == null ? "" : (": " + n.getSummary())).append("\n"));
            return sb.toString();
        });

        String rolePreamble = "你是严谨的投资决策评审, 正在对单只标的的短期(未来1~4周)走势做独立裁决。"
                + "你可以使用工具 RAG_SEARCH(检索历史理财与核心波动知识库) 与 NEWS_SEARCH(检索最新财经新闻) 来补充证据。";
        String finalSchema = "{\"verdict\":\"UP或DOWN(不允许中立)\",\"confidence\":0到1之间的数字,\"reason\":\"裁决理由\"}";

        int maxSteps = 4;
        for (int i = 1; i <= rounds; i++) {
            String task = "第 " + i + " 轮裁决。标的: " + assetName + "(" + assetCode + ")\n"
                    + "【思维树】\n" + treeText + "\n"
                    + "【历史信息】\n" + safe(historySummary) + "\n"
                    + "【RAG 初始检索】\n" + safe(ragContext) + "\n"
                    + "【最新新闻】\n" + safe(newsSummary) + "\n"
                    + "【当前持仓与盈亏】\n" + safe(holdingSummary) + "\n"
                    + "请通过 ReAct 方式, 必要时调用工具补充证据, 最终给出 UP/DOWN 裁决。";

            VoteResult v = new VoteResult();
            v.setRound(i);
            try {
                ReActResult rr = reActEngine.run(config, rolePreamble, task, finalSchema, tools, maxSteps);
                v.setReactSteps(rr.getSteps());
                JsonNode node = JsonExtractor.extract(objectMapper, rr.getFinalAnswer());
                if (node != null) {
                    String verdict = node.path("verdict").asText("").trim().toUpperCase();
                    if (!verdict.equals("UP") && !verdict.equals("DOWN")) {
                        verdict = verdict.contains("DOWN") ? "DOWN" : "UP";
                    }
                    v.setVerdict(verdict);
                    v.setConfidence(clamp01(node.path("confidence").asDouble(0.5)));
                    v.setReason(node.path("reason").asText(""));
                } else {
                    v.setVerdict("UP");
                    v.setConfidence(0.5);
                    v.setReason("ReAct 结果解析失败, 默认中性偏多。");
                }
            } catch (Exception e) {
                log.warn("第 {} 轮 ReAct 裁决失败: {}", i, e.getMessage());
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

    private List<Map<String, Object>> safeRetrieve(String query, int k) {
        try {
            return ragService.retrieve(query, k);
        } catch (Exception e) {
            log.warn("RAG 检索失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private String formatRag(List<Map<String, Object>> snippets) {
        if (snippets == null || snippets.isEmpty()) {
            return "(RAG 知识库暂无相关资料)";
        }
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> s : snippets) {
            sb.append("- [").append(s.get("type")).append(" | 相关度").append(s.get("score"))
                    .append("] ").append(s.get("content")).append("\n");
        }
        return sb.toString();
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
