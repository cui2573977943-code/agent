package com.aifinance.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 预测记录(思维树 + 多轮验证结果)。
 */
@Data
@Entity
@Table(name = "prediction")
public class Prediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id")
    private Long assetId;

    @Column(name = "asset_code")
    private String assetCode;

    /** UP / DOWN / UNCERTAIN */
    private String conclusion;

    /** INCREASE / DECREASE / HOLD */
    private String action;

    private BigDecimal confidence;

    private Integer rounds;

    @Column(name = "agree_count")
    private Integer agreeCount;

    @Column(columnDefinition = "TEXT")
    private String decomposition;

    @Column(name = "thought_tree", columnDefinition = "LONGTEXT")
    private String thoughtTree;

    @Column(columnDefinition = "LONGTEXT")
    private String reasoning;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
