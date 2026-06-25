package com.aifinance.repository;

import com.aifinance.entity.TransactionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;

public interface TransactionRepository extends JpaRepository<TransactionRecord, Long> {

    List<TransactionRecord> findByAssetIdOrderByTradeDateDescIdDesc(Long assetId);

    List<TransactionRecord> findAllByOrderByTradeDateDescIdDesc();

    @Query("select coalesce(sum(t.realizedProfit), 0) from TransactionRecord t")
    BigDecimal sumRealizedProfit();
}
