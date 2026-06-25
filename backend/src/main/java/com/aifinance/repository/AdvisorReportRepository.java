package com.aifinance.repository;

import com.aifinance.entity.AdvisorReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdvisorReportRepository extends JpaRepository<AdvisorReport, Long> {
    List<AdvisorReport> findTop20ByTypeOrderByCreatedAtDesc(String type);

    List<AdvisorReport> findTop30ByOrderByCreatedAtDesc();
}
