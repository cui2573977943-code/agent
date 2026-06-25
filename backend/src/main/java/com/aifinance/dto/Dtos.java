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
