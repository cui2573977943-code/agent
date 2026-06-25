package com.aifinance.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户持仓。
 */
@Data
@Entity
@Table(name = "holding")
public class Holding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id")
    private Long assetId;

    private BigDecimal shares;

    @Column(name = "avg_cost")
    private BigDecimal avgCost;

    @Column(name = "invested_amount")
    private BigDecimal investedAmount;

    @Column(name = "realized_profit")
    private BigDecimal realizedProfit;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (shares == null) shares = BigDecimal.ZERO;
        if (avgCost == null) avgCost = BigDecimal.ZERO;
        if (investedAmount == null) investedAmount = BigDecimal.ZERO;
        if (realizedProfit == null) realizedProfit = BigDecimal.ZERO;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
