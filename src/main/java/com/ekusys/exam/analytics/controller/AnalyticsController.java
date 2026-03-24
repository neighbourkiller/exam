package com.ekusys.exam.analytics.controller;

import com.ekusys.exam.analytics.dto.ClassTrendItem;
import com.ekusys.exam.analytics.dto.ScoreDistributionItem;
import com.ekusys.exam.analytics.dto.WrongTopicItem;
import com.ekusys.exam.analytics.service.AnalyticsService;
import com.ekusys.exam.common.api.ApiResponse;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/exams/{examId}/score-distribution")
    public ApiResponse<List<ScoreDistributionItem>> scoreDistribution(@PathVariable Long examId) {
        return ApiResponse.ok(analyticsService.scoreDistribution(examId));
    }

    @GetMapping("/exams/{examId}/class-trend")
    public ApiResponse<List<ClassTrendItem>> classTrend(@PathVariable Long examId) {
        return ApiResponse.ok(analyticsService.classTrend(examId));
    }

    @GetMapping("/exams/{examId}/wrong-topics")
    public ApiResponse<List<WrongTopicItem>> wrongTopics(@PathVariable Long examId) {
        return ApiResponse.ok(analyticsService.wrongTopics(examId));
    }
}
