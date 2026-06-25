package com.aifinance.controller;

import com.aifinance.common.ApiResponse;
import com.aifinance.dto.Dtos;
import com.aifinance.entity.RagDocument;
import com.aifinance.service.rag.RagService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * RAG 知识库接口(历史理财 + 核心波动)。
 */
@RestController
@RequestMapping("/rag")
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    /** 重建向量索引 */
    @PostMapping("/rebuild")
    public ApiResponse<Map<String, Object>> rebuild() {
        int count = ragService.rebuildIndex();
        return ApiResponse.ok(Map.of("documentCount", count));
    }

    /** 列出已索引的源文档 */
    @GetMapping("/documents")
    public ApiResponse<List<RagDocument>> documents() {
        return ApiResponse.ok(ragService.listDocuments());
    }

    /** 调试: 检索相关知识片段 */
    @PostMapping("/search")
    public ApiResponse<List<Map<String, Object>>> search(@Valid @RequestBody Dtos.RagSearchRequest req) {
        int max = req.getMaxResults() == null ? 6 : req.getMaxResults();
        return ApiResponse.ok(ragService.retrieve(req.getQuery(), max));
    }
}
