package com.ekusys.exam.exam.service;

import com.ekusys.exam.common.enums.ExamStatus;
import com.ekusys.exam.common.exception.BusinessException;
import com.ekusys.exam.exam.dto.StartExamResponse;
import com.ekusys.exam.repository.entity.Exam;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExamStartService {

    private static final Logger log = LoggerFactory.getLogger(ExamStartService.class);

    private final ExamAccessService examAccessService;
    private final ExamStatusService examStatusService;
    private final ExamSessionService examSessionService;
    private final ExamQuestionAssembler examQuestionAssembler;

    public ExamStartService(ExamAccessService examAccessService,
                            ExamStatusService examStatusService,
                            ExamSessionService examSessionService,
                            ExamQuestionAssembler examQuestionAssembler) {
        this.examAccessService = examAccessService;
        this.examStatusService = examStatusService;
        this.examSessionService = examSessionService;
        this.examQuestionAssembler = examQuestionAssembler;
    }

    @Transactional
    public StartExamResponse startExam(Long examId) {
        Long studentId = examAccessService.getCurrentUserId();
        Exam exam = examAccessService.ensureExam(examId);
        examAccessService.checkStudentAccess(examId, studentId);

        LocalDateTime now = LocalDateTime.now();
        examStatusService.refreshExamStatusByTime(exam, now);
        if (now.isBefore(exam.getStartTime())) {
            throw new BusinessException("考试尚未开始");
        }
        if (now.isAfter(exam.getEndTime())) {
            throw new BusinessException("考试已结束");
        }
        if (ExamStatus.DRAFT.name().equals(exam.getStatus())) {
            throw new BusinessException("考试未发布");
        }
        if (ExamStatus.TERMINATED.name().equals(exam.getStatus())) {
            throw new BusinessException("考试已终止");
        }
        if (ExamStatus.FINISHED.name().equals(exam.getStatus())) {
            throw new BusinessException("考试已结束");
        }

        examSessionService.startAnsweringSession(examId, studentId, now);
        examSessionService.ensureInProgressSubmission(examId, studentId);
        log.info("Exam started: examId={}, studentId={}", examId, studentId);

        return StartExamResponse.builder()
            .examId(exam.getId())
            .examName(exam.getName())
            .durationMinutes(exam.getDurationMinutes())
            .startTime(exam.getStartTime())
            .endTime(exam.getEndTime())
            .questions(examQuestionAssembler.assembleQuestions(exam.getPaperId(), examId, studentId))
            .build();
    }
}
