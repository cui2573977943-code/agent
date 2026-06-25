package com.aifinance.service;

import com.aifinance.entity.AiConfig;
import com.aifinance.repository.AiConfigRepository;
import com.aifinance.service.ai.AiClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * AI 接入配置管理。
 */
@Service
public class AiConfigService {

    private final AiConfigRepository repository;
    private final AiClient aiClient;

    public AiConfigService(AiConfigRepository repository, AiClient aiClient) {
        this.repository = repository;
        this.aiClient = aiClient;
    }

    /**
     * 获取当前启用的配置(供 Agent 使用), 不存在则抛出友好提示。
     */
    public AiConfig getActiveConfigOrThrow() {
        return repository.findFirstByEnabledTrueOrderByUpdatedAtDesc()
                .orElseThrow(() -> new IllegalArgumentException("尚未配置 AI 模型, 请先在'AI 配置'页面填写 url 和 key"));
    }

    /**
     * 获取当前启用配置(可能为空)。
     */
    public AiConfig getActiveConfig() {
        return repository.findFirstByEnabledTrueOrderByUpdatedAtDesc().orElse(null);
    }

    /**
     * 保存或更新配置。始终保持单条启用配置。
     */
    public AiConfig save(AiConfig input) {
        if (input.getBaseUrl() == null || input.getBaseUrl().isBlank()) {
            throw new IllegalArgumentException("AI url 不能为空");
        }
        if (input.getApiKey() == null || input.getApiKey().isBlank()) {
            throw new IllegalArgumentException("AI key 不能为空");
        }
        AiConfig config = repository.findFirstByEnabledTrueOrderByUpdatedAtDesc().orElse(new AiConfig());
        config.setName(input.getName() == null ? "默认配置" : input.getName());
        config.setBaseUrl(input.getBaseUrl().trim());
        config.setApiKey(input.getApiKey().trim());
        config.setModel(input.getModel() == null || input.getModel().isBlank()
                ? "gpt-4o-mini" : input.getModel().trim());
        config.setTemperature(input.getTemperature() == null
                ? new BigDecimal("0.30") : input.getTemperature());
        config.setMaxTokens(input.getMaxTokens() == null ? 2048 : input.getMaxTokens());
        config.setEnabled(true);
        return repository.save(config);
    }

    /**
     * 测试连通性。
     */
    public boolean test() {
        return aiClient.ping(getActiveConfigOrThrow());
    }

    /**
     * 返回脱敏后的配置(key 部分隐藏)。
     */
    public AiConfig getMaskedActiveConfig() {
        AiConfig config = getActiveConfig();
        if (config == null) {
            return null;
        }
        AiConfig masked = new AiConfig();
        masked.setId(config.getId());
        masked.setName(config.getName());
        masked.setBaseUrl(config.getBaseUrl());
        masked.setApiKey(maskKey(config.getApiKey()));
        masked.setModel(config.getModel());
        masked.setTemperature(config.getTemperature());
        masked.setMaxTokens(config.getMaxTokens());
        masked.setEnabled(config.getEnabled());
        masked.setUpdatedAt(config.getUpdatedAt());
        return masked;
    }

    private String maskKey(String key) {
        if (key == null || key.length() <= 8) {
            return "****";
        }
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}
