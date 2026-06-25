package com.aifinance.controller;

import com.aifinance.common.ApiResponse;
import com.aifinance.dto.Dtos;
import com.aifinance.entity.Prediction;
import com.aifinance.service.PredictionService;
import com.aifinance.service.ai.tot.PredictionOutcome;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 预测接口(思维树 + 多轮验证)。
 */
@RestController
@RequestMapping("/predictions")
public class PredictionController {

    private final PredictionService predictionService;

    public PredictionController(PredictionService predictionService) {
        this.predictionService = predictionService;
    }

    /** 对单个标的发起预测 */
    @PostMapping
    public ApiResponse<PredictionOutcome> predict(@Valid @RequestBody Dtos.PredictRequest req) {
        boolean fetchNews = req.getFetchNews() == null || req.getFetchNews();
        return ApiResponse.ok(predictionService.predict(req.getAssetCode(), fetchNews));
    }

    /** 历史预测记录 */
    @GetMapping
    public ApiResponse<List<Prediction>> history(@RequestParam(required = false) String assetCode) {
        return ApiResponse.ok(predictionService.history(assetCode));
    }
}
