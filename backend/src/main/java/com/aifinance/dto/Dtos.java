package com.aifinance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 集中存放各类请求/响应 DTO。
 */
public final class Dtos {

    private Dtos() {
    }

    /** 资产创建请求 */
    @Data
    public static class AssetRequest {
        @NotBlank(message = "代码不能为空")
        private String code;
        @NotBlank(message = "名称不能为空")
        private String name;
        @NotBlank(message = "类型不能为空(STOCK/FUND)")
        private String type;
        private String market;
        private BigDecimal latestPrice;
        private BigDecimal prevClose;
    }

    /** 交易请求(买入/卖出) */
    @Data
    public static class TransactionRequest {
        @NotNull(message = "资产ID不能为空")
        private Long assetId;
        @NotBlank(message = "类型不能为空(BUY/SELL)")
        private String type;
        @NotNull @Positive(message = "份额必须大于0")
        private BigDecimal shares;
        @NotNull @Positive(message = "价格必须大于0")
        private BigDecimal price;
        private BigDecimal fee;
        private LocalDate tradeDate;
        private String note;
    }

    /** AI 预测请求 */
    @Data
    public static class PredictRequest {
        @NotBlank(message = "资产代码不能为空")
        private String assetCode;
        /** 是否在线抓取新闻 */
        private Boolean fetchNews = true;
    }

    /** 理财规划请求 */
    @Data
    public static class FinancePlanRequest {
        @NotNull @Positive(message = "月工资必须大于0")
        private BigDecimal salary;
        private BigDecimal expense;
    }

    /** 财务档案(余额等)请求 */
    @Data
    public static class ProfileRequest {
        private BigDecimal cashBalance;
        private BigDecimal monthlyIncome;
        private BigDecimal monthlyExpense;
        private String riskPreference;
        private String note;
    }

    /** 理财建议请求(用户意向) */
    @Data
    public static class AdviceRequest {
        private String intention;
        /** 是否抓取财经新闻 */
        private Boolean fetchNews = true;
    }

    /** RAG 检索请求 */
    @Data
    public static class RagSearchRequest {
        @NotBlank(message = "查询内容不能为空")
        private String query;
        private Integer maxResults = 6;
    }

    /** 价格历史录入(单点) */
    @Data
    public static class PricePointRequest {
        @NotNull(message = "交易日期不能为空")
        private LocalDate tradeDate;
        private BigDecimal open;
        @NotNull(message = "收盘价不能为空")
        private BigDecimal close;
        private BigDecimal high;
        private BigDecimal low;
        private Long volume;
    }

    /** 工资记录请求 */
    @Data
    public static class SalaryRequest {
        @NotBlank(message = "月份不能为空(yyyy-MM)")
        private String month;
        @NotNull @Positive(message = "工资必须大于0")
        private BigDecimal salary;
        private BigDecimal expense;
        private String note;
    }
}
