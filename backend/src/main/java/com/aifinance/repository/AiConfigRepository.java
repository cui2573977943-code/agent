package com.aifinance.repository;

import com.aifinance.entity.AiConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiConfigRepository extends JpaRepository<AiConfig, Long> {
    Optional<AiConfig> findFirstByEnabledTrueOrderByUpdatedAtDesc();
}
