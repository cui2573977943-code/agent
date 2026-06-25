package com.aifinance.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * RAG 知识文档(历史理财情况 + 核心股票/基金波动)。
 * 作为检索增强的源文档持久化, 向量索引在应用内存中重建。
 */
@Data
@Entity
@Table(name = "rag_document")
public class RagDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doc_type")
    private String docType;

    @Column(name = "ref_code")
    private String refCode;

    private String title;

    @Column(columnDefinition = "LONGTEXT")
    private String content;

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
