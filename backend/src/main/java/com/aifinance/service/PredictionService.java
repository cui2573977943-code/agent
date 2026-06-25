package com.aifinance.service;

import com.aifinance.entity.*;
import com.aifinance.repository.*;
import com.aifinance.service.ai.tot.PredictionAgent;
import com.aifinance.service.ai.tot.PredictionOutcome;
import com.aifinance.service.crawler.NewsCrawlerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 单标的预测编排: 组装上下文 -> 调用思维树 Agent -> 落库。
 */
@Service
public class PredictionService {

    private static final Logger log = LoggerFactory.getLogger(PredictionService.class);

    private final AssetService assetService;
    private final HoldingRepository holdingRepository;
    private final TransactionRepository transactionRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final NewsRepository newsRepository;
    private final PredictionRepository predictionRepository;
    private final AiConfigService aiConfigService;
    private final PredictionAgent predictionAgent;
    private final NewsCrawlerService newsCrawlerService;
    private final ObjectMapper objectMapper;

    public PredictionService(AssetService assetService, HoldingRepository holdingRepository,
                             TransactionRepository transactionRepository,
                             PriceHistoryRepository priceHistoryRepository,
                             NewsRepository newsRepository, PredictionRepository predictionRepository,
                             AiConfigService aiConfigService, PredictionAgent predictionAgent,
                             NewsCrawlerService newsCrawlerService, ObjectMapper objectMapper) {
        this.assetService = assetService;
        this.holdingRepository = holdingRepository;
        this.transactionRepository = transactionRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.newsRepository = newsRepository;
        this.predictionRepository = predictionRepository;
        this.aiConfigService = aiConfigService;
        this.predictionAgent = predictionAgent;
        this.newsCrawlerService = newsCrawlerService;
        this.objectMapper = objectMapper;
    }

    public PredictionOutcome predict(String assetCode, boolean fetchNews) {
        AiConfig config = aiConfigService.getActiveConfigOrThrow();
        Asset asset = assetService.getByCode(assetCode);

        String historySummary = buildHistorySummary(asset);
        String newsSummary = buildNewsSummary(asset, fetchNews);
        String holdingSummary = buildHoldingSummary(asset);

        PredictionOutcome outcome = predictionAgent.run(config, asset.getName(), asset.getCode(),
                historySummary, newsSummary, holdingSummary);

        save(asset, outcome);
        return outcome;
    }

    private void save(Asset asset, PredictionOutcome outcome) {
        try {
            Prediction p = new Prediction();
            p.setAssetId(asset.getId());
            p.setAssetCode(asset.getCode());
            p.setConclusion(outcome.getConclusion());
            p.setAction(outcome.getAction());
            p.setConfidence(BigDecimal.valueOf(outcome.getConfidence()));
            p.setRounds(outcome.getRounds());
            p.setAgreeCount(outcome.getAgreeCount());
            p.setDecomposition(String.join(" | ", outcome.getDecomposition()));
            p.setThoughtTree(objectMapper.writeValueAsString(
                    predictionAgent.treeToMap(outcome.getThoughtTree())));
            p.setReasoning(outcome.getReasoning());
            predictionRepository.save(p);
        } catch (Exception e) {
            log.warn("预测结果落库失败: {}", e.getMessage());
        }
    }

    public List<Prediction> history(String assetCode) {
        if (assetCode == null || assetCode.isBlank()) {
            return predictionRepository.findTop50ByOrderByCreatedAtDesc();
        }
        return predictionRepository.findTop20ByAssetCodeOrderByCreatedAtDesc(assetCode);
    }

    // ---------------- 上下文组装 ----------------

    private String buildHistorySummary(Asset asset) {
        StringBuilder sb = new StringBuilder();
        sb.append("名称: ").append(asset.getName())
                .append(", 代码: ").append(asset.getCode())
                .append(", 类型: ").append(asset.getType()).append("\n");
        if (asset.getLatestPrice() != null) {
            sb.append("最新价: ").append(asset.getLatestPrice());
            if (asset.getChangePct() != null) {
                sb.append(", 当日涨跌幅: ").append(asset.getChangePct()).append("%");
            }
            sb.append("\n");
        }
        List<PriceHistory> prices = priceHistoryRepository
                .findTop60ByAssetIdOrderByTradeDateDesc(asset.getId());
        if (!prices.isEmpty()) {
            sb.append("近 ").append(prices.size()).append(" 个交易日收盘价(由近及远): ");
            sb.append(prices.stream()
                    .map(ph -> ph.getTradeDate() + ":" + ph.getClosePrice())
                    .collect(Collectors.joining(", ")));
            sb.append("\n");
        } else {
            sb.append("(暂无历史价格数据)\n");
        }
        List<TransactionRecord> txns = transactionRepository
                .findByAssetIdOrderByTradeDateDescIdDesc(asset.getId());
        if (!txns.isEmpty()) {
            sb.append("历史交易记录: ");
            sb.append(txns.stream().limit(20)
                    .map(t -> t.getTradeDate() + " " + t.getType() + " " + t.getShares()
                            + "份@" + t.getPrice() + (t.getRealizedProfit().signum() != 0
                            ? "(实现盈亏" + t.getRealizedProfit() + ")" : ""))
                    .collect(Collectors.joining("; ")));
            sb.append("\n");
        }
        return sb.toString();
    }

    private String buildNewsSummary(Asset asset, boolean fetchNews) {
        List<NewsArticle> news;
        if (fetchNews) {
            news = newsCrawlerService.fetchAndStore(asset.getCode(), asset.getName());
            if (news.isEmpty()) {
                news = newsRepository.findTop20ByAssetCodeOrderByCreatedAtDesc(asset.getCode());
            }
        } else {
            news = newsRepository.findTop20ByAssetCodeOrderByCreatedAtDesc(asset.getCode());
        }
        if (news.isEmpty()) {
            return "(暂无可用新闻, 请基于历史数据与常识谨慎判断)";
        }
        return news.stream().limit(8)
                .map(n -> "- [" + (n.getSource() == null ? "" : n.getSource()) + "] "
                        + n.getTitle() + (n.getSummary() == null || n.getSummary().isBlank()
                        ? "" : (": " + n.getSummary())))
                .collect(Collectors.joining("\n"));
    }

    private String buildHoldingSummary(Asset asset) {
        Optional<Holding> opt = holdingRepository.findByAssetId(asset.getId());
        if (opt.isEmpty() || opt.get().getShares() == null || opt.get().getShares().signum() == 0) {
            return "当前未持有该标的。";
        }
        Holding h = opt.get();
        StringBuilder sb = new StringBuilder();
        sb.append("持有份额: ").append(h.getShares())
                .append(", 平均成本: ").append(h.getAvgCost())
                .append(", 持仓成本: ").append(h.getInvestedAmount())
                .append(", 已实现盈亏: ").append(h.getRealizedProfit());
        if (asset.getLatestPrice() != null) {
            BigDecimal marketValue = asset.getLatestPrice().multiply(h.getShares());
            BigDecimal unrealized = marketValue.subtract(h.getInvestedAmount());
            sb.append(", 当前市值: ").append(marketValue.setScale(2, java.math.RoundingMode.HALF_UP))
                    .append(", 浮动盈亏: ").append(unrealized.setScale(2, java.math.RoundingMode.HALF_UP));
        }
        return sb.toString();
    }
}
