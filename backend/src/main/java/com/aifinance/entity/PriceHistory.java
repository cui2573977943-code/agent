package com.aifinance.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 价格历史。
 */
@Data
@Entity
@Table(name = "price_history")
public class PriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id")
    private Long assetId;

    @Column(name = "trade_date")
    private LocalDate tradeDate;

    @Column(name = "open_price")
    private BigDecimal openPrice;

    @Column(name = "close_price")
    private BigDecimal closePrice;

    @Column(name = "high_price")
    private BigDecimal highPrice;

    @Column(name = "low_price")
    private BigDecimal lowPrice;

    private Long volume;

    @Column(name = "change_pct")
    private BigDecimal changePct;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
