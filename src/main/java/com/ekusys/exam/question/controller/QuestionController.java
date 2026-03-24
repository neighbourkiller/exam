package com.ekusys.exam.question.controller;

import com.ekusys.exam.common.api.ApiResponse;
import com.ekusys.exam.common.api.PageResponse;
import com.ekusys.exam.question.dto.QuestionCreateRequest;
import com.ekusys.exam.question.dto.QuestionQueryRequest;
import com.ekusys.exam.question.dto.QuestionUpdateRequest;
import com.ekusys.exam.question.dto.QuestionView;
import com.ekusys.exam.question.service.QuestionService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/questions")
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @PostMapping("/query")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<PageResponse<QuestionView>> query(@RequestBody QuestionQueryRequest request) {
        return ApiResponse.ok(questionService.query(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<QuestionView> getById(@PathVariable Long id) {
        return ApiResponse.ok(questionService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Long> create(@Valid @RequestBody QuestionCreateRequest request) {
        return ApiResponse.ok("创建成功", questionService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody QuestionUpdateRequest request) {
        questionService.update(id, request);
        return ApiResponse.ok("更新成功", null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        questionService.delete(id);
        return ApiResponse.ok("删除成功", null);
    }
}
