package com.aifinance.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 理财规划记录(思维树 + 多轮验证结果)。
 */
@Data
@Entity
@Table(name = "finance_plan")
public class FinancePlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "monthly_salary")
    private BigDecimal monthlySalary;

    @Column(name = "monthly_expense")
    private BigDecimal monthlyExpense;

    /** CONSERVATIVE / BALANCED / AGGRESSIVE */
    @Column(name = "risk_style")
    private String riskStyle;

    @Column(name = "emergency_fund")
    private BigDecimal emergencyFund;

    @Column(name = "investable_amount")
    private BigDecimal investableAmount;

    private BigDecimal confidence;

    private Integer rounds;

    @Column(columnDefinition = "TEXT")
    private String decomposition;

    @Column(name = "thought_tree", columnDefinition = "LONGTEXT")
    private String thoughtTree;

    @Column(name = "plan_detail", columnDefinition = "LONGTEXT")
    private String planDetail;

    @Column(columnDefinition = "LONGTEXT")
    private String reasoning;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
