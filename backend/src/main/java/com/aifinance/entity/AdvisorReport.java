package com.aifinance.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 理财分析报告(一键汇总 / 建议结果)。
 */
@Data
@Entity
@Table(name = "advisor_report")
public class AdvisorReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** SUMMARY / ADVICE */
    private String type;

    @Column(length = 1000)
    private String intention;

    @Column(columnDefinition = "LONGTEXT")
    private String metrics;

    @Column(columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
