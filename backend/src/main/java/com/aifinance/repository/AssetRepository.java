package com.aifinance.repository;

import com.aifinance.entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssetRepository extends JpaRepository<Asset, Long> {
    Optional<Asset> findByCode(String code);

    boolean existsByCode(String code);
}
