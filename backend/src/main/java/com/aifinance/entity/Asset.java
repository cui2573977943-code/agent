package com.aifinance.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 资产(股票 / 基金)。
 */
@Data
@Entity
@Table(name = "asset")
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code;

    private String name;

    /** STOCK / FUND */
    private String type;

    private String market;

    @Column(name = "latest_price")
    private BigDecimal latestPrice;

    @Column(name = "prev_close")
    private BigDecimal prevClose;

    @Column(name = "change_pct")
    private BigDecimal changePct;

    @Column(name = "price_updated_at")
    private LocalDateTime priceUpdatedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
