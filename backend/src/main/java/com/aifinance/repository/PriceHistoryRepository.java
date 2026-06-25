package com.aifinance.repository;

import com.aifinance.entity.PriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {
    List<PriceHistory> findTop60ByAssetIdOrderByTradeDateDesc(Long assetId);
}
