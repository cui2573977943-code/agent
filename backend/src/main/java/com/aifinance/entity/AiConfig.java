package com.aifinance.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 模型接入配置。
 */
@Data
@Entity
@Table(name = "ai_config")
public class AiConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(name = "base_url")
    private String baseUrl;

    @Column(name = "api_key")
    private String apiKey;

    private String model;

    private BigDecimal temperature;

    @Column(name = "max_tokens")
    private Integer maxTokens;

    private Boolean enabled;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (enabled == null) enabled = true;
        if (temperature == null) temperature = new BigDecimal("0.30");
        if (maxTokens == null) maxTokens = 2048;
        if (model == null) model = "gpt-4o-mini";
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
