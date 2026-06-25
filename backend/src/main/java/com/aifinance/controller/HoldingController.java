package com.aifinance.controller;

import com.aifinance.common.ApiResponse;
import com.aifinance.dto.Dtos;
import com.aifinance.dto.Views;
import com.aifinance.entity.TransactionRecord;
import com.aifinance.service.HoldingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 持仓 / 交易 / 盈亏接口。
 */
@RestController
@RequestMapping("/holdings")
public class HoldingController {

    private final HoldingService holdingService;

    public HoldingController(HoldingService holdingService) {
        this.holdingService = holdingService;
    }

    /** 持仓列表(含实时盈亏) */
    @GetMapping
    public ApiResponse<List<Views.HoldingView>> list() {
        return ApiResponse.ok(holdingService.listHoldings());
    }

    /** 组合汇总 */
    @GetMapping("/summary")
    public ApiResponse<Views.PortfolioSummary> summary() {
        return ApiResponse.ok(holdingService.summary());
    }

    /** 记录买入/卖出 */
    @PostMapping("/transactions")
    public ApiResponse<TransactionRecord> trade(@Valid @RequestBody Dtos.TransactionRequest req) {
        return ApiResponse.ok(holdingService.recordTransaction(req));
    }

    /** 某资产交易明细 */
    @GetMapping("/{assetId}/transactions")
    public ApiResponse<List<TransactionRecord>> transactions(@PathVariable Long assetId) {
        return ApiResponse.ok(holdingService.transactionsOf(assetId));
    }

    /** 全部交易明细 */
    @GetMapping("/transactions")
    public ApiResponse<List<TransactionRecord>> allTransactions() {
        return ApiResponse.ok(holdingService.allTransactions());
    }
}
