package com.aifinance.controller;

import com.aifinance.common.ApiResponse;
import com.aifinance.dto.Dtos;
import com.aifinance.entity.AdvisorReport;
import com.aifinance.entity.UserProfile;
import com.aifinance.service.WealthAdvisorService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 理财分析与建议接口。
 */
@RestController
@RequestMapping("/advisor")
public class AdvisorController {

    private final WealthAdvisorService advisorService;

    public AdvisorController(WealthAdvisorService advisorService) {
        this.advisorService = advisorService;
    }

    /** 获取财务档案(余额等) */
    @GetMapping("/profile")
    public ApiResponse<UserProfile> getProfile() {
        return ApiResponse.ok(advisorService.getProfile());
    }

    /** 保存财务档案(余额等) */
    @PostMapping("/profile")
    public ApiResponse<UserProfile> saveProfile(@RequestBody Dtos.ProfileRequest req) {
        return ApiResponse.ok(advisorService.saveProfile(req));
    }

    /** 一键汇总分析已有理财信息 */
    @PostMapping("/summary")
    public ApiResponse<Map<String, Object>> summarize() {
        return ApiResponse.ok(advisorService.summarize());
    }

    /** 基于汇总信息、财经新闻与用户意向给出后续建议 */
    @PostMapping("/advice")
    public ApiResponse<Map<String, Object>> advise(@RequestBody Dtos.AdviceRequest req) {
        boolean fetchNews = req.getFetchNews() == null || req.getFetchNews();
        return ApiResponse.ok(advisorService.advise(req.getIntention(), fetchNews));
    }

    /** 历史报告 */
    @GetMapping("/reports")
    public ApiResponse<List<AdvisorReport>> reports(@RequestParam(required = false) String type) {
        return ApiResponse.ok(advisorService.reports(type));
    }
}
