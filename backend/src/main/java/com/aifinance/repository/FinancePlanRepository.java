package com.aifinance.repository;

import com.aifinance.entity.FinancePlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FinancePlanRepository extends JpaRepository<FinancePlan, Long> {
    List<FinancePlan> findTop50ByOrderByCreatedAtDesc();
}
