package com.aifinance.service.ai;

import com.aifinance.entity.AiConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容的对话补全客户端。
 * 用户在前端填写的 base_url / api_key / model 会通过 {@link AiConfig} 传入。
 */
@Component
public class AiClient {

    private static final Logger log = LoggerFactory.getLogger(AiClient.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * 发起一次对话补全。
     *
     * @param config   AI 接入配置
     * @param messages 对话消息
     * @return 模型返回的文本内容
     */
    public String chat(AiConfig config, List<ChatMessage> messages) {
        if (config == null) {
            throw new IllegalArgumentException("未配置 AI 模型, 请先在页面填写 AI url 和 key");
        }
        if (config.getBaseUrl() == null || config.getBaseUrl().isBlank()) {
            throw new IllegalArgumentException("AI url 不能为空");
        }
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new IllegalArgumentException("AI key 不能为空");
        }

        String endpoint = buildEndpoint(config.getBaseUrl());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getModel() == null ? "gpt-4o-mini" : config.getModel());
        body.put("temperature", config.getTemperature() == null
                ? 0.3 : config.getTemperature().doubleValue());
        body.put("max_tokens", config.getMaxTokens() == null ? 2048 : config.getMaxTokens());

        List<Map<String, String>> msgList = new ArrayList<>();
        for (ChatMessage m : messages) {
            Map<String, String> mm = new LinkedHashMap<>();
            mm.put("role", m.getRole());
            mm.put("content", m.getContent());
            msgList.add(mm);
        }
        body.put("messages", msgList);

        try {
            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(90))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("AI 接口返回非 2xx: {} -> {}", response.statusCode(), response.body());
                throw new IllegalStateException("AI 接口调用失败(" + response.statusCode() + "): "
                        + truncate(response.body()));
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            if (contentNode.isMissingNode() || contentNode.isNull()) {
                throw new IllegalStateException("AI 返回内容为空: " + truncate(response.body()));
            }
            return contentNode.asText();
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("调用 AI 接口异常: " + e.getMessage(), e);
        }
    }

    /**
     * 校验配置是否可用(发起一次极简调用)。
     */
    public boolean ping(AiConfig config) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system("You are a connectivity test."));
        messages.add(ChatMessage.user("ping"));
        AiConfig probe = shallowCopyWithLimit(config);
        String result = chat(probe, messages);
        return result != null;
    }

    private AiConfig shallowCopyWithLimit(AiConfig config) {
        AiConfig c = new AiConfig();
        c.setBaseUrl(config.getBaseUrl());
        c.setApiKey(config.getApiKey());
        c.setModel(config.getModel());
        c.setTemperature(BigDecimal.ZERO);
        c.setMaxTokens(16);
        return c;
    }

    /**
     * 兼容用户填写 https://host 或 https://host/v1 或完整 .../chat/completions。
     */
    private String buildEndpoint(String baseUrl) {
        String url = baseUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.endsWith("/chat/completions")) {
            return url;
        }
        if (url.endsWith("/v1")) {
            return url + "/chat/completions";
        }
        return url + "/v1/chat/completions";
    }

    private String truncate(String s) {
        if (s == null) return "";
        return s.length() > 500 ? s.substring(0, 500) + "..." : s;
    }
}
