package com.aifinance.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工资记录。
 */
@Data
@Entity
@Table(name = "salary_record")
public class SalaryRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** yyyy-MM */
    @Column(name = "`month`")
    private String month;

    private BigDecimal salary;

    private BigDecimal expense;

    private BigDecimal saving;

    private String note;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (expense == null) expense = BigDecimal.ZERO;
        if (saving == null) saving = BigDecimal.ZERO;
    }
}
