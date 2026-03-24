package com.ekusys.exam.paper.controller;

import com.ekusys.exam.common.api.ApiResponse;
import com.ekusys.exam.paper.dto.AutoGeneratePaperRequest;
import com.ekusys.exam.paper.dto.ManualCreatePaperRequest;
import com.ekusys.exam.paper.dto.PaperDetailView;
import com.ekusys.exam.paper.service.PaperService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/papers")
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
public class PaperController {

    private final PaperService paperService;

    public PaperController(PaperService paperService) {
        this.paperService = paperService;
    }

    @PostMapping("/manual")
    public ApiResponse<Long> manualCreate(@Valid @RequestBody ManualCreatePaperRequest request) {
        return ApiResponse.ok("组卷成功", paperService.createManual(request));
    }

    @PostMapping("/auto-generate")
    public ApiResponse<Long> autoCreate(@Valid @RequestBody AutoGeneratePaperRequest request) {
        return ApiResponse.ok("组卷成功", paperService.autoGenerate(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<PaperDetailView> getDetail(@PathVariable Long id) {
        return ApiResponse.ok(paperService.getDetail(id));
    }
}
