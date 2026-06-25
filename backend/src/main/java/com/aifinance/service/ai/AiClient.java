package com.aifinance.service.ai;

import com.aifinance.entity.AiConfig;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 LangChain4j 的 AI 客户端。
 * <p>
 * 用户在前端填写的 base_url / api_key / model 会通过 {@link AiConfig} 传入,
 * 这里据此动态构建 LangChain4j 的 {@link OpenAiChatModel}(OpenAI 兼容)。
 * 对外保留 {@code chat(AiConfig, List<ChatMessage>)} 接口, 上层 Agent 无需感知 LangChain4j。
 */
@Component
public class AiClient {

    private static final Logger log = LoggerFactory.getLogger(AiClient.class);

    /** 以配置指纹缓存已构建的模型, 避免多轮调用重复创建 HTTP 客户端 */
    private final Map<String, ChatModel> modelCache = new ConcurrentHashMap<>();

    /**
     * 发起一次对话补全。
     *
     * @param config   AI 接入配置
     * @param messages 对话消息(本项目内部的消息结构)
     * @return 模型返回的文本内容
     */
    public String chat(AiConfig config, List<ChatMessage> messages) {
        ChatModel model = buildModel(config);
        List<dev.langchain4j.data.message.ChatMessage> lcMessages = toLangChainMessages(messages);
        try {
            ChatResponse response = model.chat(lcMessages);
            if (response == null || response.aiMessage() == null) {
                throw new IllegalStateException("AI 返回内容为空");
            }
            String text = response.aiMessage().text();
            if (text == null) {
                throw new IllegalStateException("AI 返回内容为空");
            }
            return text;
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("调用 AI 接口异常: " + e.getMessage(), e);
        }
    }

    /**
     * 测试连通性(发起一次极简调用)。
     */
    public boolean ping(AiConfig config) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system("You are a connectivity test."));
        messages.add(ChatMessage.user("ping"));
        String result = chat(config, messages);
        return result != null;
    }

    // ---------------- 内部实现 ----------------

    private ChatModel buildModel(AiConfig config) {
        validate(config);
        String key = fingerprint(config);
        return modelCache.computeIfAbsent(key, k -> {
            double temperature = config.getTemperature() == null
                    ? 0.3 : config.getTemperature().doubleValue();
            int maxTokens = config.getMaxTokens() == null ? 2048 : config.getMaxTokens();
            return OpenAiChatModel.builder()
                    .baseUrl(normalizeBaseUrl(config.getBaseUrl()))
                    .apiKey(config.getApiKey())
                    .modelName(config.getModel() == null ? "gpt-4o-mini" : config.getModel())
                    .temperature(temperature)
                    .maxTokens(maxTokens)
                    .timeout(Duration.ofSeconds(90))
                    .build();
        });
    }

    private void validate(AiConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("未配置 AI 模型, 请先在页面填写 AI url 和 key");
        }
        if (config.getBaseUrl() == null || config.getBaseUrl().isBlank()) {
            throw new IllegalArgumentException("AI url 不能为空");
        }
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new IllegalArgumentException("AI key 不能为空");
        }
    }

    private List<dev.langchain4j.data.message.ChatMessage> toLangChainMessages(List<ChatMessage> messages) {
        List<dev.langchain4j.data.message.ChatMessage> result = new ArrayList<>();
        for (ChatMessage m : messages) {
            String role = m.getRole() == null ? "user" : m.getRole().toLowerCase();
            switch (role) {
                case "system" -> result.add(SystemMessage.from(m.getContent()));
                case "assistant" -> result.add(AiMessage.from(m.getContent()));
                default -> result.add(UserMessage.from(m.getContent()));
            }
        }
        return result;
    }

    /**
     * 规范化 baseUrl: LangChain4j 会在 baseUrl 后追加 /chat/completions,
     * 故这里确保 baseUrl 形如 https://host/v1。
     */
    private String normalizeBaseUrl(String baseUrl) {
        String url = baseUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.endsWith("/chat/completions")) {
            url = url.substring(0, url.length() - "/chat/completions".length());
        }
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (!url.endsWith("/v1")) {
            url = url + "/v1";
        }
        return url;
    }

    private String fingerprint(AiConfig config) {
        return String.join("|",
                String.valueOf(config.getBaseUrl()),
                String.valueOf(config.getApiKey()),
                String.valueOf(config.getModel()),
                String.valueOf(config.getTemperature()),
                String.valueOf(config.getMaxTokens()));
    }
}
