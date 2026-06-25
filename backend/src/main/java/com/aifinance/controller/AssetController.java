package com.aifinance.controller;

import com.aifinance.common.ApiResponse;
import com.aifinance.dto.Dtos;
import com.aifinance.entity.Asset;
import com.aifinance.service.AssetService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 资产(股票/基金)接口。
 */
@RestController
@RequestMapping("/assets")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @GetMapping
    public ApiResponse<List<Asset>> list() {
        return ApiResponse.ok(assetService.list());
    }

    @PostMapping
    public ApiResponse<Asset> create(@Valid @RequestBody Dtos.AssetRequest req) {
        return ApiResponse.ok(assetService.create(req));
    }

    @PutMapping("/{id}/price")
    public ApiResponse<Asset> updatePrice(@PathVariable Long id,
                                          @RequestBody Map<String, BigDecimal> body) {
        BigDecimal price = body.get("latestPrice");
        if (price == null) {
            throw new IllegalArgumentException("latestPrice 不能为空");
        }
        return ApiResponse.ok(assetService.updatePrice(id, price));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        assetService.delete(id);
        return ApiResponse.ok(null);
    }

    /** 批量录入价格历史(用于核心波动 RAG) */
    @PostMapping("/{id}/price-history")
    public ApiResponse<Map<String, Object>> addPriceHistory(
            @PathVariable Long id, @RequestBody List<Dtos.PricePointRequest> points) {
        int n = assetService.addPriceHistory(id, points);
        return ApiResponse.ok(Map.of("saved", n));
    }

    /** 查询价格历史 */
    @GetMapping("/{id}/price-history")
    public ApiResponse<List<com.aifinance.entity.PriceHistory>> priceHistory(@PathVariable Long id) {
        return ApiResponse.ok(assetService.priceHistory(id));
    }
}
