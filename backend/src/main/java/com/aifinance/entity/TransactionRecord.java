package com.aifinance.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 交易记录(买入 / 卖出)。
 */
@Data
@Entity
@Table(name = "transaction_record")
public class TransactionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id")
    private Long assetId;

    /** BUY / SELL */
    private String type;

    private BigDecimal shares;

    private BigDecimal price;

    private BigDecimal amount;

    private BigDecimal fee;

    @Column(name = "realized_profit")
    private BigDecimal realizedProfit;

    @Column(name = "trade_date")
    private LocalDate tradeDate;

    private String note;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (fee == null) fee = BigDecimal.ZERO;
        if (realizedProfit == null) realizedProfit = BigDecimal.ZERO;
        if (tradeDate == null) tradeDate = LocalDate.now();
    }
}
