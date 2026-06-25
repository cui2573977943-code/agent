package com.aifinance.service;

import com.aifinance.dto.Dtos;
import com.aifinance.dto.Views;
import com.aifinance.entity.*;
import com.aifinance.repository.*;
import com.aifinance.service.ai.AiClient;
import com.aifinance.service.ai.ChatMessage;
import com.aifinance.service.ai.JsonExtractor;
import com.aifinance.service.crawler.NewsCrawlerService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 理财分析与建议服务。
 * <p>
 * 基于用户收入(工资记录/档案)、现金余额(档案)与理财盈亏(持仓/交易):
 * <ol>
 *   <li>{@link #summarize()} 一键汇总分析已有理财信息(数值由后端精确计算, AI 负责文字解读);</li>
 *   <li>{@link #advise(String, boolean)} 结合汇总信息、爬取的财经新闻与用户意向, 给出后续建议。</li>
 * </ol>
 * AI 调用统一经由基于 LangChain4j 的 {@link AiClient}。
 */
@Service
public class WealthAdvisorService {

    private static final Logger log = LoggerFactory.getLogger(WealthAdvisorService.class);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final UserProfileRepository profileRepository;
    private final AdvisorReportRepository reportRepository;
    private final SalaryRepository salaryRepository;
    private final HoldingService holdingService;
    private final AssetRepository assetRepository;
    private final AiConfigService aiConfigService;
    private final AiClient aiClient;
    private final NewsCrawlerService newsCrawlerService;
    private final ObjectMapper objectMapper;

    public WealthAdvisorService(UserProfileRepository profileRepository,
                                AdvisorReportRepository reportRepository,
                                SalaryRepository salaryRepository, HoldingService holdingService,
                                AssetRepository assetRepository, AiConfigService aiConfigService,
                                AiClient aiClient, NewsCrawlerService newsCrawlerService,
                                ObjectMapper objectMapper) {
        this.profileRepository = profileRepository;
        this.reportRepository = reportRepository;
        this.salaryRepository = salaryRepository;
        this.holdingService = holdingService;
        this.assetRepository = assetRepository;
        this.aiConfigService = aiConfigService;
        this.aiClient = aiClient;
        this.newsCrawlerService = newsCrawlerService;
        this.objectMapper = objectMapper;
    }

    // ---------------- 财务档案 ----------------

    public UserProfile getProfile() {
        return profileRepository.findFirstByOrderByIdAsc().orElseGet(() -> {
            UserProfile p = new UserProfile();
            p.setCashBalance(BigDecimal.ZERO);
            return profileRepository.save(p);
        });
    }

    public UserProfile saveProfile(Dtos.ProfileRequest req) {
        UserProfile p = getProfile();
        if (req.getCashBalance() != null) p.setCashBalance(req.getCashBalance());
        if (req.getMonthlyIncome() != null) p.setMonthlyIncome(req.getMonthlyIncome());
        if (req.getMonthlyExpense() != null) p.setMonthlyExpense(req.getMonthlyExpense());
        if (req.getRiskPreference() != null) p.setRiskPreference(req.getRiskPreference());
        if (req.getNote() != null) p.setNote(req.getNote());
        return profileRepository.save(p);
    }

    // ---------------- 一键汇总分析 ----------------

    public Map<String, Object> summarize() {
        AiConfig config = aiConfigService.getActiveConfigOrThrow();
        Map<String, Object> metrics = computeMetrics();
        String metricsText = renderMetrics(metrics);
        String holdingsText = renderHoldings();

        String sys = "你是资深个人财务分析师。基于给定的财务指标与持仓, 对用户当前理财状况做客观汇总解读。"
                + "严格只输出 JSON: {\"narrative\":\"整体汇总(200字内)\","
                + "\"highlights\":[\"关键发现1\",\"关键发现2\"],"
                + "\"healthScore\":0到100的整数}。";
        String user = "【财务指标】\n" + metricsText + "\n【持仓明细】\n" + holdingsText;

        Map<String, Object> result = new LinkedHashMap<>(metrics);
        result.put("narrative", "");
        result.put("highlights", new ArrayList<>());
        result.put("healthScore", null);
        try {
            String resp = aiClient.chat(config, List.of(ChatMessage.system(sys), ChatMessage.user(user)));
            JsonNode node = JsonExtractor.extract(objectMapper, resp);
            if (node != null) {
                result.put("narrative", node.path("narrative").asText(""));
                List<String> highlights = new ArrayList<>();
                node.path("highlights").forEach(h -> highlights.add(h.asText()));
                result.put("highlights", highlights);
                if (node.has("healthScore") && !node.path("healthScore").isNull()) {
                    result.put("healthScore", node.path("healthScore").asInt());
                }
            }
        } catch (Exception e) {
            log.warn("汇总分析 AI 调用失败: {}", e.getMessage());
            result.put("narrative", "AI 解读失败(" + e.getMessage() + "), 以下为系统计算的客观指标。");
        }
        saveReport("SUMMARY", null, metrics, result);
        return result;
    }

    // ---------------- 基于新闻与意向的建议 ----------------

    public Map<String, Object> advise(String intention, boolean fetchNews) {
        AiConfig config = aiConfigService.getActiveConfigOrThrow();
        Map<String, Object> metrics = computeMetrics();
        String metricsText = renderMetrics(metrics);
        String holdingsText = renderHoldings();
        List<String> newsList = collectNews(fetchNews);
        String newsText = newsList.isEmpty() ? "(暂无可用财经新闻)" : String.join("\n", newsList);
        String intent = (intention == null || intention.isBlank())
                ? "(用户未填写具体意向, 请给出通用稳健建议)" : intention.trim();

        String sys = "你是专业理财顾问。基于用户财务汇总、当前持仓、最新财经新闻与用户的理财意向, "
                + "给出可执行的后续建议。请兼顾收益与风险, 结合新闻判断市场环境。"
                + "严格只输出 JSON: {"
                + "\"healthScore\":0到100整数,"
                + "\"riskLevel\":\"LOW|MEDIUM|HIGH\","
                + "\"summary\":\"总体建议(200字内)\","
                + "\"strengths\":[\"优势\"],"
                + "\"risks\":[\"风险点\"],"
                + "\"allocations\":[{\"category\":\"类别\",\"current\":0到1,\"suggested\":0到1,\"reason\":\"\"}],"
                + "\"actions\":[{\"title\":\"行动项\",\"detail\":\"说明\",\"priority\":\"HIGH|MEDIUM|LOW\"}],"
                + "\"newsInsights\":[\"从新闻得到的判断\"]}。";
        String user = "【用户理财意向】\n" + intent + "\n"
                + "【财务汇总指标】\n" + metricsText + "\n"
                + "【当前持仓】\n" + holdingsText + "\n"
                + "【最新财经新闻】\n" + newsText + "\n"
                + "请输出建议 JSON。";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("metrics", metrics);
        result.put("intention", intent);
        result.put("newsUsed", newsList);
        try {
            String resp = aiClient.chat(config, List.of(ChatMessage.system(sys), ChatMessage.user(user)));
            JsonNode node = JsonExtractor.extract(objectMapper, resp);
            if (node != null) {
                result.put("healthScore", node.path("healthScore").isNull() ? null : node.path("healthScore").asInt());
                result.put("riskLevel", node.path("riskLevel").asText("MEDIUM"));
                result.put("summary", node.path("summary").asText(""));
                result.put("strengths", toStringList(node.path("strengths")));
                result.put("risks", toStringList(node.path("risks")));
                result.put("allocations", toAllocationList(node.path("allocations")));
                result.put("actions", toActionList(node.path("actions")));
                result.put("newsInsights", toStringList(node.path("newsInsights")));
            } else {
                fillAdviceFallback(result, "AI 返回解析失败");
            }
        } catch (Exception e) {
            log.warn("理财建议 AI 调用失败: {}", e.getMessage());
            fillAdviceFallback(result, e.getMessage());
        }
        saveReport("ADVICE", intent, metrics, result);
        return result;
    }

    public List<AdvisorReport> reports(String type) {
        if (type == null || type.isBlank()) {
            return reportRepository.findTop30ByOrderByCreatedAtDesc();
        }
        return reportRepository.findTop20ByTypeOrderByCreatedAtDesc(type.toUpperCase());
    }

    // ---------------- 指标计算 ----------------

    private Map<String, Object> computeMetrics() {
        UserProfile profile = getProfile();
        Views.PortfolioSummary ps = holdingService.summary();

        BigDecimal cash = nz(profile.getCashBalance());
        BigDecimal marketValue = nz(ps.getTotalMarketValue());
        BigDecimal invested = nz(ps.getTotalInvested());
        BigDecimal unrealized = nz(ps.getTotalUnrealizedProfit());
        BigDecimal realized = nz(ps.getTotalRealizedProfit());
        BigDecimal totalProfit = nz(ps.getTotalProfit());
        BigDecimal netWorth = cash.add(marketValue);

        BigDecimal income = profile.getMonthlyIncome();
        BigDecimal expense = profile.getMonthlyExpense();
        SalaryRecord latest = salaryRepository.findAllByOrderByMonthDesc().stream().findFirst().orElse(null);
        if (income == null && latest != null) income = latest.getSalary();
        if (expense == null && latest != null) expense = latest.getExpense();
        income = nz(income);
        expense = nz(expense);
        BigDecimal saving = income.subtract(expense);
        BigDecimal savingRate = income.signum() > 0
                ? saving.multiply(HUNDRED).divide(income, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cashBalance", cash);
        m.put("investmentValue", marketValue);
        m.put("investedAmount", invested);
        m.put("netWorth", netWorth);
        m.put("unrealizedProfit", unrealized);
        m.put("realizedProfit", realized);
        m.put("totalProfit", totalProfit);
        m.put("totalProfitRate", nz(ps.getTotalProfitRate()));
        m.put("monthlyIncome", income);
        m.put("monthlyExpense", expense);
        m.put("monthlySaving", saving);
        m.put("savingRate", savingRate);
        m.put("holdingCount", ps.getHoldingCount());
        m.put("riskPreference", profile.getRiskPreference());
        return m;
    }

    private String renderMetrics(Map<String, Object> m) {
        return "现金余额: " + m.get("cashBalance") + " 元\n"
                + "投资市值: " + m.get("investmentValue") + " 元 (持仓成本 " + m.get("investedAmount") + ")\n"
                + "净资产: " + m.get("netWorth") + " 元\n"
                + "浮动盈亏: " + m.get("unrealizedProfit") + " 元, 已实现盈亏: " + m.get("realizedProfit") + " 元\n"
                + "理财总盈亏: " + m.get("totalProfit") + " 元 (" + m.get("totalProfitRate") + "%)\n"
                + "月收入: " + m.get("monthlyIncome") + ", 月支出: " + m.get("monthlyExpense")
                + ", 月结余: " + m.get("monthlySaving") + " (储蓄率 " + m.get("savingRate") + "%)\n"
                + "持仓数量: " + m.get("holdingCount") + ", 风险偏好: " + m.get("riskPreference");
    }

    private String renderHoldings() {
        List<Views.HoldingView> holdings = holdingService.listHoldings();
        if (holdings.isEmpty()) {
            return "(当前无持仓)";
        }
        return holdings.stream()
                .map(h -> "- " + h.getName() + "(" + h.getCode() + "," + h.getType() + "): 市值"
                        + h.getMarketValue() + ", 浮动盈亏" + h.getUnrealizedProfit()
                        + ", 已实现" + h.getRealizedProfit() + ", 总盈亏率" + h.getProfitRate() + "%")
                .collect(Collectors.joining("\n"));
    }

    private List<String> collectNews(boolean fetchNews) {
        List<String> result = new ArrayList<>();
        if (!fetchNews) {
            return result;
        }
        Set<String> keywords = new LinkedHashSet<>();
        keywords.add("财经 理财 基金 市场");
        assetRepository.findAll().stream().limit(3).forEach(a -> keywords.add(a.getName()));
        for (String kw : keywords) {
            try {
                newsCrawlerService.fetchAndStore(null, kw).forEach(n ->
                        result.add("- [" + (n.getSource() == null ? "" : n.getSource()) + "] " + n.getTitle()
                                + (n.getSummary() == null || n.getSummary().isBlank() ? "" : (": " + n.getSummary()))));
            } catch (Exception e) {
                log.warn("财经新闻抓取失败({}): {}", kw, e.getMessage());
            }
            if (result.size() >= 10) break;
        }
        return result.stream().distinct().limit(10).collect(Collectors.toList());
    }

    // ---------------- 持久化与工具 ----------------

    private void saveReport(String type, String intention, Map<String, Object> metrics, Map<String, Object> content) {
        try {
            AdvisorReport r = new AdvisorReport();
            r.setType(type);
            r.setIntention(intention);
            r.setMetrics(objectMapper.writeValueAsString(metrics));
            r.setContent(objectMapper.writeValueAsString(content));
            reportRepository.save(r);
        } catch (Exception e) {
            log.warn("理财报告落库失败: {}", e.getMessage());
        }
    }

    private void fillAdviceFallback(Map<String, Object> result, String reason) {
        result.put("healthScore", null);
        result.put("riskLevel", "MEDIUM");
        result.put("summary", "AI 建议生成失败(" + reason + ")。建议: 预留 3-6 个月应急金, 其余资金按风险偏好分散配置于宽基指数基金与货币/债券基金, 定期再平衡。");
        result.put("strengths", new ArrayList<>());
        result.put("risks", List.of("AI 服务暂不可用, 请检查 AI 配置后重试"));
        result.put("allocations", new ArrayList<>());
        result.put("actions", new ArrayList<>());
        result.put("newsInsights", new ArrayList<>());
    }

    private List<String> toStringList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(n -> list.add(n.asText()));
        }
        return list;
    }

    private List<Map<String, Object>> toAllocationList(JsonNode node) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(n -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("category", n.path("category").asText(""));
                m.put("current", n.path("current").asDouble(0));
                m.put("suggested", n.path("suggested").asDouble(0));
                m.put("reason", n.path("reason").asText(""));
                list.add(m);
            });
        }
        return list;
    }

    private List<Map<String, Object>> toActionList(JsonNode node) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(n -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("title", n.path("title").asText(""));
                m.put("detail", n.path("detail").asText(""));
                m.put("priority", n.path("priority").asText("MEDIUM"));
                list.add(m);
            });
        }
        return list;
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
