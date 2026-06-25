package com.aifinance.repository;

import com.aifinance.entity.Prediction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PredictionRepository extends JpaRepository<Prediction, Long> {
    List<Prediction> findTop20ByAssetCodeOrderByCreatedAtDesc(String assetCode);

    List<Prediction> findTop50ByOrderByCreatedAtDesc();
}
