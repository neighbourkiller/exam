package com.ekusys.exam.exam.controller;

import com.ekusys.exam.common.api.ApiResponse;
import com.ekusys.exam.common.audit.AuditOperation;
import com.ekusys.exam.exam.dto.AntiCheatEvidenceUploadView;
import com.ekusys.exam.exam.dto.AntiCheatEventRequest;
import com.ekusys.exam.exam.dto.ExamCreateRequest;
import com.ekusys.exam.exam.dto.ProctoringDispositionRequest;
import com.ekusys.exam.exam.dto.ProctoringDispositionView;
import com.ekusys.exam.exam.dto.ProctoringOverviewView;
import com.ekusys.exam.exam.dto.ProctoringStudentTimelineView;
import com.ekusys.exam.exam.dto.ProctoringStudentView;
import com.ekusys.exam.exam.dto.SnapshotAckView;
import com.ekusys.exam.exam.dto.SnapshotRequest;
import com.ekusys.exam.exam.dto.StartExamResponse;
import com.ekusys.exam.exam.dto.StudentExamResultView;
import com.ekusys.exam.exam.dto.StudentExamView;
import com.ekusys.exam.exam.dto.TeacherExamView;
import com.ekusys.exam.exam.dto.TeachingClassOptionView;
import com.ekusys.exam.exam.dto.SubmitExamRequest;
import com.ekusys.exam.exam.dto.SubmitResultView;
import com.ekusys.exam.exam.service.ExamAntiCheatEvidenceService;
import com.ekusys.exam.exam.service.ExamService;
import com.ekusys.exam.exam.service.ExamProctoringService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/exams")
public class ExamController {

    private final ExamService examService;
    private final ExamProctoringService examProctoringService;
    private final ExamAntiCheatEvidenceService examAntiCheatEvidenceService;

    public ExamController(ExamService examService,
                          ExamProctoringService examProctoringService,
                          ExamAntiCheatEvidenceService examAntiCheatEvidenceService) {
        this.examService = examService;
        this.examProctoringService = examProctoringService;
        this.examAntiCheatEvidenceService = examAntiCheatEvidenceService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditOperation(action = "EXAM_CREATE", targetType = "EXAM", targetId = "#result.data", detail = "#request.name")
    public ApiResponse<Long> createExam(@Valid @RequestBody ExamCreateRequest request) {
        return ApiResponse.ok("创建成功", examService.createExam(request));
    }

    @PostMapping("/{examId}/publish")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditOperation(action = "EXAM_PUBLISH", targetType = "EXAM", targetId = "#examId")
    public ApiResponse<Void> publish(@PathVariable Long examId) {
        examService.publishExam(examId);
        return ApiResponse.ok("发布成功", null);
    }

    @PostMapping("/{examId}/terminate")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditOperation(action = "EXAM_TERMINATE", targetType = "EXAM", targetId = "#examId")
    public ApiResponse<Void> terminate(@PathVariable Long examId) {
        examService.terminateExam(examId);
        return ApiResponse.ok("终止成功", null);
    }

    @GetMapping("/student")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<List<StudentExamView>> studentExams() {
        return ApiResponse.ok(examService.listStudentExams());
    }

    @GetMapping("/student/results")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<List<StudentExamResultView>> studentResults() {
        return ApiResponse.ok(examService.listStudentExamResults());
    }

    @GetMapping("/teacher")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<List<TeacherExamView>> teacherExams() {
        return ApiResponse.ok(examService.listTeacherExams());
    }

    @GetMapping("/{examId}/proctoring/overview")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<ProctoringOverviewView> proctoringOverview(@PathVariable Long examId) {
        return ApiResponse.ok(examProctoringService.getOverview(examId));
    }

    @GetMapping("/{examId}/proctoring/students")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<List<ProctoringStudentView>> proctoringStudents(@PathVariable Long examId) {
        return ApiResponse.ok(examProctoringService.listStudents(examId));
    }

    @GetMapping("/{examId}/proctoring/students/{studentId}/timeline")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<ProctoringStudentTimelineView> proctoringTimeline(@PathVariable Long examId,
                                                                         @PathVariable Long studentId) {
        return ApiResponse.ok(examProctoringService.getStudentTimeline(examId, studentId));
    }

    @PutMapping("/{examId}/proctoring/students/{studentId}/disposition")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditOperation(action = "PROCTORING_DISPOSITION_UPDATE", targetType = "PROCTORING_DISPOSITION",
        targetId = "#examId + ':' + #studentId", detail = "#request.status")
    public ApiResponse<ProctoringDispositionView> updateProctoringDisposition(@PathVariable Long examId,
                                                                              @PathVariable Long studentId,
                                                                              @Valid @RequestBody ProctoringDispositionRequest request) {
        return ApiResponse.ok("处置记录已保存", examProctoringService.updateStudentDisposition(examId, studentId, request));
    }

    @GetMapping("/teaching-classes")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<List<TeachingClassOptionView>> teachingClasses() {
        return ApiResponse.ok(examService.listTeachingClasses());
    }

    @PostMapping("/{examId}/start")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<StartExamResponse> start(@PathVariable Long examId) {
        return ApiResponse.ok(examService.startExam(examId));
    }

    @PostMapping("/{examId}/snapshot")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<SnapshotAckView> snapshot(@PathVariable Long examId, @Valid @RequestBody SnapshotRequest request) {
        return ApiResponse.ok("快照已保存", examService.saveSnapshot(examId, request));
    }

    @PostMapping("/{examId}/anti-cheat-events")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<Void> antiCheat(@PathVariable Long examId, @Valid @RequestBody AntiCheatEventRequest request) {
        examService.recordAntiCheatEvent(examId, request);
        return ApiResponse.ok("记录成功", null);
    }

    @PostMapping(value = "/{examId}/anti-cheat-evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<AntiCheatEvidenceUploadView> antiCheatEvidence(@PathVariable Long examId,
                                                                      @RequestParam("file") MultipartFile file,
                                                                      @RequestParam("source") String source,
                                                                      @RequestParam("eventType") String eventType) {
        return ApiResponse.ok("证据上传成功", examAntiCheatEvidenceService.upload(examId, file, source, eventType));
    }

    @PostMapping("/{examId}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<SubmitResultView> submit(@PathVariable Long examId, @Valid @RequestBody SubmitExamRequest request) {
        return ApiResponse.ok("交卷成功", examService.submit(examId, request));
    }
}
