package com.aifinance.controller;

import com.aifinance.common.ApiResponse;
import com.aifinance.entity.AiConfig;
import com.aifinance.service.AiConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI 接入配置接口: 用户在前端填写 AI url / key / 模型。
 */
@RestController
@RequestMapping("/ai-config")
public class AiConfigController {

    private final AiConfigService aiConfigService;

    public AiConfigController(AiConfigService aiConfigService) {
        this.aiConfigService = aiConfigService;
    }

    /** 获取当前配置(key 脱敏) */
    @GetMapping
    public ApiResponse<AiConfig> get() {
        return ApiResponse.ok(aiConfigService.getMaskedActiveConfig());
    }

    /** 保存/更新配置 */
    @PostMapping
    public ApiResponse<AiConfig> save(@RequestBody AiConfig config) {
        aiConfigService.save(config);
        return ApiResponse.ok(aiConfigService.getMaskedActiveConfig());
    }

    /** 测试连通性 */
    @PostMapping("/test")
    public ApiResponse<Map<String, Object>> test() {
        boolean ok = aiConfigService.test();
        return ApiResponse.ok(Map.of("connected", ok));
    }
}
