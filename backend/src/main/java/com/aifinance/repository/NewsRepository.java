package com.aifinance.repository;

import com.aifinance.entity.NewsArticle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NewsRepository extends JpaRepository<NewsArticle, Long> {
    List<NewsArticle> findTop20ByAssetCodeOrderByCreatedAtDesc(String assetCode);
}
