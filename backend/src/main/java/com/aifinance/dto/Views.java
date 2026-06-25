package com.aifinance.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 响应视图对象。
 */
public final class Views {

    private Views() {
    }

    /** 持仓视图(含实时市值与盈亏) */
    @Data
    public static class HoldingView {
        private Long assetId;
        private String code;
        private String name;
        private String type;
        private String market;
        private BigDecimal shares;
        private BigDecimal avgCost;
        private BigDecimal latestPrice;
        private BigDecimal changePct;
        private BigDecimal investedAmount;   // 当前持仓成本
        private BigDecimal marketValue;      // 当前市值
        private BigDecimal unrealizedProfit; // 浮动盈亏
        private BigDecimal realizedProfit;   // 已实现盈亏
        private BigDecimal totalProfit;      // 总盈亏
        private BigDecimal profitRate;       // 总盈亏率(%)
    }

    /** 组合汇总 */
    @Data
    public static class PortfolioSummary {
        private BigDecimal totalInvested;
        private BigDecimal totalMarketValue;
        private BigDecimal totalUnrealizedProfit;
        private BigDecimal totalRealizedProfit;
        private BigDecimal totalProfit;
        private BigDecimal totalProfitRate;
        private int holdingCount;
    }
}
