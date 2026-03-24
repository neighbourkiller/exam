package com.ekusys.exam.grading.controller;

import com.ekusys.exam.common.api.ApiResponse;
import com.ekusys.exam.grading.dto.PendingAnswerView;
import com.ekusys.exam.grading.dto.SubjectiveScoreRequest;
import com.ekusys.exam.grading.service.GradingService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/grading")
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
public class GradingController {

    private final GradingService gradingService;

    public GradingController(GradingService gradingService) {
        this.gradingService = gradingService;
    }

    @GetMapping("/pending")
    public ApiResponse<List<PendingAnswerView>> pending() {
        return ApiResponse.ok(gradingService.pendingAnswers());
    }

    @PostMapping("/{submissionId}/subjective-score")
    public ApiResponse<Void> score(@PathVariable Long submissionId, @Valid @RequestBody SubjectiveScoreRequest request) {
        gradingService.scoreSubjective(submissionId, request);
        return ApiResponse.ok("评分成功", null);
    }
}
