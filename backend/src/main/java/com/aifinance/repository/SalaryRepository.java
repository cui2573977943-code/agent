package com.aifinance.repository;

import com.aifinance.entity.SalaryRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalaryRepository extends JpaRepository<SalaryRecord, Long> {
    List<SalaryRecord> findAllByOrderByMonthDesc();
}
