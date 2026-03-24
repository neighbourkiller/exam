package com.ekusys.exam.exam.controller;

import com.ekusys.exam.common.api.ApiResponse;
import com.ekusys.exam.exam.dto.AntiCheatEventRequest;
import com.ekusys.exam.exam.dto.ExamCreateRequest;
import com.ekusys.exam.exam.dto.SnapshotRequest;
import com.ekusys.exam.exam.dto.StartExamResponse;
import com.ekusys.exam.exam.dto.StudentExamView;
import com.ekusys.exam.exam.dto.TeacherExamView;
import com.ekusys.exam.exam.dto.SubmitExamRequest;
import com.ekusys.exam.exam.dto.SubmitResultView;
import com.ekusys.exam.exam.service.ExamService;
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
@RequestMapping("/api/v1/exams")
public class ExamController {

    private final ExamService examService;

    public ExamController(ExamService examService) {
        this.examService = examService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Long> createExam(@Valid @RequestBody ExamCreateRequest request) {
        return ApiResponse.ok("创建成功", examService.createExam(request));
    }

    @PostMapping("/{examId}/publish")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Void> publish(@PathVariable Long examId) {
        examService.publishExam(examId);
        return ApiResponse.ok("发布成功", null);
    }

    @GetMapping("/student")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<List<StudentExamView>> studentExams() {
        return ApiResponse.ok(examService.listStudentExams());
    }

    @GetMapping("/teacher")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<List<TeacherExamView>> teacherExams() {
        return ApiResponse.ok(examService.listTeacherExams());
    }

    @PostMapping("/{examId}/start")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<StartExamResponse> start(@PathVariable Long examId) {
        return ApiResponse.ok(examService.startExam(examId));
    }

    @PostMapping("/{examId}/snapshot")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<Void> snapshot(@PathVariable Long examId, @Valid @RequestBody SnapshotRequest request) {
        examService.saveSnapshot(examId, request);
        return ApiResponse.ok("快照已保存", null);
    }

    @PostMapping("/{examId}/anti-cheat-events")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<Void> antiCheat(@PathVariable Long examId, @Valid @RequestBody AntiCheatEventRequest request) {
        examService.recordAntiCheatEvent(examId, request);
        return ApiResponse.ok("记录成功", null);
    }

    @PostMapping("/{examId}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<SubmitResultView> submit(@PathVariable Long examId, @Valid @RequestBody SubmitExamRequest request) {
        return ApiResponse.ok("交卷成功", examService.submit(examId, request));
    }
}
