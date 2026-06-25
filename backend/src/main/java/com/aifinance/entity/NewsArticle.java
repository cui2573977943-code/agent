package com.aifinance.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 爬取的新闻。
 */
@Data
@Entity
@Table(name = "news_article")
public class NewsArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_code")
    private String assetCode;

    private String keyword;

    @Column(length = 500)
    private String title;

    @Column(length = 1000)
    private String url;

    private String source;

    @Column(columnDefinition = "TEXT")
    private String summary;

    private String sentiment;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
