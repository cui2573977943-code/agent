package com.aifinance.service.rag;

import com.aifinance.entity.*;
import com.aifinance.repository.*;
import com.aifinance.service.HoldingService;
import com.aifinance.dto.Views;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG 服务: 为"历史理财情况"与"核心股票/基金波动"建立检索增强知识库。
 * <p>
 * 使用本地嵌入模型(AllMiniLM-L6-v2)向量化源文档并存入内存向量库(InMemoryEmbeddingStore)。
 * AI 预测时先调用 {@link #retrieve} 取回最相关的资料, 注入到思维链/思维树与 ReAct 推理中。
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private final EmbeddingModel embeddingModel;
    private final RagDocumentRepository ragDocumentRepository;
    private final AssetRepository assetRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final HoldingRepository holdingRepository;
    private final TransactionRepository transactionRepository;
    private final SalaryRepository salaryRepository;
    private final PredictionRepository predictionRepository;
    private final FinancePlanRepository financePlanRepository;
    private final UserProfileRepository userProfileRepository;
    private final HoldingService holdingService;

    private volatile InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
    private volatile boolean indexed = false;

    public RagService(EmbeddingModel embeddingModel, RagDocumentRepository ragDocumentRepository,
                      AssetRepository assetRepository, PriceHistoryRepository priceHistoryRepository,
                      HoldingRepository holdingRepository, TransactionRepository transactionRepository,
                      SalaryRepository salaryRepository, PredictionRepository predictionRepository,
                      FinancePlanRepository financePlanRepository,
                      UserProfileRepository userProfileRepository, HoldingService holdingService) {
        this.embeddingModel = embeddingModel;
        this.ragDocumentRepository = ragDocumentRepository;
        this.assetRepository = assetRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.holdingRepository = holdingRepository;
        this.transactionRepository = transactionRepository;
        this.salaryRepository = salaryRepository;
        this.predictionRepository = predictionRepository;
        this.financePlanRepository = financePlanRepository;
        this.userProfileRepository = userProfileRepository;
        this.holdingService = holdingService;
    }

    /**
     * 重建 RAG 索引: 从业务数据生成源文档 -> 落库 -> 向量化 -> 写入内存向量库。
     *
     * @return 已索引文档数
     */
    public synchronized int rebuildIndex() {
        List<RagDocument> docs = buildDocuments();

        // 落库(覆盖式)
        try {
            ragDocumentRepository.deleteAllInBatch();
            ragDocumentRepository.saveAll(docs);
        } catch (Exception e) {
            log.warn("RAG 文档落库失败(不影响内存索引): {}", e.getMessage());
        }

        InMemoryEmbeddingStore<TextSegment> newStore = new InMemoryEmbeddingStore<>();
        if (!docs.isEmpty()) {
            List<TextSegment> segments = docs.stream()
                    .map(d -> TextSegment.from(d.getContent(), Metadata.from(Map.of(
                            "type", nullSafe(d.getDocType()),
                            "refCode", nullSafe(d.getRefCode()),
                            "title", nullSafe(d.getTitle())))))
                    .collect(Collectors.toList());
            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
            newStore.addAll(embeddings, segments);
        }
        this.store = newStore;
        this.indexed = true;
        log.info("RAG 索引重建完成, 文档数={}", docs.size());
        return docs.size();
    }

    /**
     * 若尚未建立索引则自动重建。
     */
    public void ensureIndexed() {
        if (!indexed) {
            rebuildIndex();
        }
    }

    /**
     * 检索最相关的知识片段。
     */
    public List<Map<String, Object>> retrieve(String query, int maxResults) {
        ensureIndexed();
        if (query == null || query.isBlank()) {
            return List.of();
        }
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(maxResults)
                .minScore(0.2)
                .build();
        EmbeddingSearchResult<TextSegment> result = store.search(request);
        List<Map<String, Object>> list = new ArrayList<>();
        for (EmbeddingMatch<TextSegment> match : result.matches()) {
            TextSegment seg = match.embedded();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("score", round2(match.score()));
            m.put("type", seg.metadata().getString("type"));
            m.put("refCode", seg.metadata().getString("refCode"));
            m.put("title", seg.metadata().getString("title"));
            m.put("content", seg.text());
            list.add(m);
        }
        return list;
    }

    /**
     * 检索并格式化为可注入提示词的文本。
     */
    public String retrieveAsContext(String query, int maxResults) {
        List<Map<String, Object>> hits = retrieve(query, maxResults);
        if (hits.isEmpty()) {
            return "(RAG 知识库暂无相关资料)";
        }
        return hits.stream()
                .map(h -> "- [" + h.get("type") + " | 相关度" + h.get("score") + "] " + h.get("content"))
                .collect(Collectors.joining("\n"));
    }

    public List<RagDocument> listDocuments() {
        return ragDocumentRepository.findAllByOrderByUpdatedAtDesc();
    }

    // ---------------- 文档构建 ----------------

    private List<RagDocument> buildDocuments() {
        List<RagDocument> docs = new ArrayList<>();
        docs.add(buildFinanceOverview());
        docs.addAll(buildSalaryDoc());

        for (Asset asset : assetRepository.findAll()) {
            RagDocument vol = buildAssetVolatility(asset);
            if (vol != null) docs.add(vol);
            RagDocument pnl = buildHoldingPnl(asset);
            if (pnl != null) docs.add(pnl);
        }

        predictionRepository.findTop50ByOrderByCreatedAtDesc().stream().limit(20).forEach(p ->
                docs.add(doc("PREDICTION_HISTORY", p.getAssetCode(),
                        "历史预测 " + p.getAssetCode(),
                        "【历史预测】" + p.getAssetCode() + " 于 " + p.getCreatedAt()
                                + " 结论=" + p.getConclusion() + ", 建议=" + p.getAction()
                                + ", 置信度=" + p.getConfidence() + "%, 多轮一致=" + p.getAgreeCount() + "/" + p.getRounds()
                                + (p.getReasoning() == null ? "" : (", 说明: " + clip(p.getReasoning(), 300))))));

        financePlanRepository.findTop50ByOrderByCreatedAtDesc().stream().limit(10).forEach(fp ->
                docs.add(doc("FINANCE_PLAN", null,
                        "历史理财规划",
                        "【历史理财规划】" + fp.getCreatedAt() + " 风格=" + fp.getRiskStyle()
                                + ", 应急金=" + fp.getEmergencyFund() + ", 可投资=" + fp.getInvestableAmount()
                                + (fp.getReasoning() == null ? "" : (", 说明: " + clip(fp.getReasoning(), 300))))));
        return docs;
    }

    private RagDocument buildFinanceOverview() {
        Views.PortfolioSummary ps = holdingService.summary();
        UserProfile profile = userProfileRepository.findFirstByOrderByIdAsc().orElse(null);
        BigDecimal cash = profile == null || profile.getCashBalance() == null
                ? BigDecimal.ZERO : profile.getCashBalance();
        StringBuilder sb = new StringBuilder("【整体财务状况】");
        sb.append("现金余额=").append(cash)
                .append(", 投资市值=").append(ps.getTotalMarketValue())
                .append(", 持仓成本=").append(ps.getTotalInvested())
                .append(", 浮动盈亏=").append(ps.getTotalUnrealizedProfit())
                .append(", 已实现盈亏=").append(ps.getTotalRealizedProfit())
                .append(", 理财总盈亏=").append(ps.getTotalProfit())
                .append("(" ).append(ps.getTotalProfitRate()).append("%)")
                .append(", 持仓数量=").append(ps.getHoldingCount());
        if (profile != null && profile.getRiskPreference() != null) {
            sb.append(", 风险偏好=").append(profile.getRiskPreference());
        }
        return doc("FINANCE_OVERVIEW", null, "整体财务状况", sb.toString());
    }

    private List<RagDocument> buildSalaryDoc() {
        List<SalaryRecord> salaries = salaryRepository.findAllByOrderByMonthDesc();
        if (salaries.isEmpty()) {
            return List.of();
        }
        String content = "【历史收入】" + salaries.stream().limit(12)
                .map(s -> s.getMonth() + ":工资" + s.getSalary() + "/支出" + s.getExpense() + "/结余" + s.getSaving())
                .collect(Collectors.joining("; "));
        return List.of(doc("SALARY", null, "历史收入与结余", content));
    }

    private RagDocument buildAssetVolatility(Asset asset) {
        List<PriceHistory> prices = priceHistoryRepository
                .findTop60ByAssetIdOrderByTradeDateDesc(asset.getId());
        StringBuilder sb = new StringBuilder("【核心波动】")
                .append(asset.getName()).append("(").append(asset.getCode())
                .append(", ").append(asset.getType()).append("): ");
        if (prices.size() >= 2) {
            // 价格按时间升序
            List<PriceHistory> asc = new ArrayList<>(prices);
            Collections.reverse(asc);
            List<Double> closes = asc.stream()
                    .map(p -> p.getClosePrice() == null ? null : p.getClosePrice().doubleValue())
                    .filter(Objects::nonNull).toList();
            if (closes.size() >= 2) {
                double first = closes.get(0);
                double last = closes.get(closes.size() - 1);
                double periodChange = first == 0 ? 0 : (last - first) / first * 100;
                double vol = dailyVolatility(closes) * 100;
                double maxDrawdown = maxDrawdown(closes) * 100;
                String trend = recentTrend(closes);
                sb.append("样本=").append(closes.size()).append("日")
                        .append(", 区间涨跌=").append(round2(periodChange)).append("%")
                        .append(", 日波动率=").append(round2(vol)).append("%")
                        .append(", 最大回撤=").append(round2(maxDrawdown)).append("%")
                        .append(", 近期趋势=").append(trend)
                        .append(", 最新收盘=").append(round2(last));
            }
        } else {
            sb.append("暂无历史价格序列");
            if (asset.getLatestPrice() != null) {
                sb.append(", 最新价=").append(asset.getLatestPrice());
            }
            if (asset.getChangePct() != null) {
                sb.append(", 当日涨跌幅=").append(asset.getChangePct()).append("%");
            }
        }
        return doc("ASSET_VOLATILITY", asset.getCode(), asset.getName() + " 波动特征", sb.toString());
    }

    private RagDocument buildHoldingPnl(Asset asset) {
        Optional<Holding> opt = holdingRepository.findByAssetId(asset.getId());
        List<TransactionRecord> txns = transactionRepository
                .findByAssetIdOrderByTradeDateDescIdDesc(asset.getId());
        if (opt.isEmpty() && txns.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("【持仓与盈亏】")
                .append(asset.getName()).append("(").append(asset.getCode()).append("): ");
        opt.ifPresent(h -> sb.append("持有=").append(h.getShares())
                .append(", 平均成本=").append(h.getAvgCost())
                .append(", 持仓成本=").append(h.getInvestedAmount())
                .append(", 已实现盈亏=").append(h.getRealizedProfit()).append("; "));
        if (!txns.isEmpty()) {
            sb.append("历史交易: ").append(txns.stream().limit(15)
                    .map(t -> t.getTradeDate() + " " + t.getType() + " " + t.getShares() + "@" + t.getPrice()
                            + (t.getRealizedProfit() != null && t.getRealizedProfit().signum() != 0
                            ? "(盈亏" + t.getRealizedProfit() + ")" : ""))
                    .collect(Collectors.joining("; ")));
        }
        return doc("HOLDING_PNL", asset.getCode(), asset.getName() + " 持仓盈亏", sb.toString());
    }

    // ---------------- 计算工具 ----------------

    private double dailyVolatility(List<Double> closes) {
        List<Double> returns = new ArrayList<>();
        for (int i = 1; i < closes.size(); i++) {
            double prev = closes.get(i - 1);
            if (prev != 0) returns.add((closes.get(i) - prev) / prev);
        }
        if (returns.isEmpty()) return 0;
        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double var = returns.stream().mapToDouble(r -> (r - mean) * (r - mean)).average().orElse(0);
        return Math.sqrt(var);
    }

    private double maxDrawdown(List<Double> closes) {
        double peak = closes.get(0);
        double maxDd = 0;
        for (double c : closes) {
            if (c > peak) peak = c;
            if (peak > 0) {
                double dd = (peak - c) / peak;
                if (dd > maxDd) maxDd = dd;
            }
        }
        return maxDd;
    }

    private String recentTrend(List<Double> closes) {
        int n = closes.size();
        int window = Math.min(5, n - 1);
        if (window <= 0) return "持平";
        double recent = closes.get(n - 1) - closes.get(n - 1 - window);
        if (recent > 0) return "上行";
        if (recent < 0) return "下行";
        return "持平";
    }

    private RagDocument doc(String type, String refCode, String title, String content) {
        RagDocument d = new RagDocument();
        d.setDocType(type);
        d.setRefCode(refCode);
        d.setTitle(title);
        d.setContent(content);
        return d;
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private String clip(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
