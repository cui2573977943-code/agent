package com.aifinance.controller;

import com.aifinance.common.ApiResponse;
import com.aifinance.dto.Dtos;
import com.aifinance.entity.FinancePlan;
import com.aifinance.entity.SalaryRecord;
import com.aifinance.service.FinancePlanService;
import com.aifinance.service.ai.tot.FinancePlanOutcome;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工资理财规划接口(思维树 + 多轮验证)。
 */
@RestController
@RequestMapping("/finance")
public class FinancePlanController {

    private final FinancePlanService financePlanService;

    public FinancePlanController(FinancePlanService financePlanService) {
        this.financePlanService = financePlanService;
    }

    /** 生成理财规划 */
    @PostMapping("/plan")
    public ApiResponse<FinancePlanOutcome> plan(@Valid @RequestBody Dtos.FinancePlanRequest req) {
        return ApiResponse.ok(financePlanService.plan(req.getSalary(), req.getExpense()));
    }

    /** 历史规划记录 */
    @GetMapping("/plans")
    public ApiResponse<List<FinancePlan>> plans() {
        return ApiResponse.ok(financePlanService.history());
    }

    /** 录入工资记录 */
    @PostMapping("/salary")
    public ApiResponse<SalaryRecord> saveSalary(@Valid @RequestBody Dtos.SalaryRequest req) {
        return ApiResponse.ok(financePlanService.saveSalary(req));
    }

    /** 工资历史 */
    @GetMapping("/salary")
    public ApiResponse<List<SalaryRecord>> salaryHistory() {
        return ApiResponse.ok(financePlanService.salaryHistory());
    }
}
