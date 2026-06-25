package com.aifinance.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * RAG 相关 Bean。
 * <p>
 * 使用本地内置的 AllMiniLM-L6-v2(ONNX) 嵌入模型, 无需额外的 embedding API,
 * 离线即可生成向量。声明为 {@code @Lazy}, 仅在首次使用 RAG 时加载模型。
 */
@Configuration
public class RagConfig {

    @Bean
    @Lazy
    public EmbeddingModel embeddingModel() {
        return new AllMiniLmL6V2EmbeddingModel();
    }
}
