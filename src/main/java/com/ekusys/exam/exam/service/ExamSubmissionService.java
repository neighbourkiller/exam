package com.ekusys.exam.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ekusys.exam.common.enums.SessionStatus;
import com.ekusys.exam.common.enums.SubmissionStatus;
import com.ekusys.exam.common.exception.BusinessException;
import com.ekusys.exam.common.util.AnswerJudgeUtil;
import com.ekusys.exam.exam.dto.AnswerPayload;
import com.ekusys.exam.exam.dto.SubmitExamRequest;
import com.ekusys.exam.exam.dto.SubmitResultView;
import com.ekusys.exam.repository.entity.Exam;
import com.ekusys.exam.repository.entity.ExamSession;
import com.ekusys.exam.repository.entity.PaperQuestion;
import com.ekusys.exam.repository.entity.Question;
import com.ekusys.exam.repository.entity.Submission;
import com.ekusys.exam.repository.entity.SubmissionAnswer;
import com.ekusys.exam.repository.mapper.PaperQuestionMapper;
import com.ekusys.exam.repository.mapper.QuestionMapper;
import com.ekusys.exam.repository.mapper.SubmissionAnswerMapper;
import com.ekusys.exam.repository.mapper.SubmissionMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExamSubmissionService {

    private static final Logger log = LoggerFactory.getLogger(ExamSubmissionService.class);

    private final ExamAccessService examAccessService;
    private final ExamSessionService examSessionService;
    private final ExamSnapshotService examSnapshotService;
    private final SubmissionMapper submissionMapper;
    private final SubmissionAnswerMapper submissionAnswerMapper;
    private final PaperQuestionMapper paperQuestionMapper;
    private final QuestionMapper questionMapper;

    public ExamSubmissionService(ExamAccessService examAccessService,
                                 ExamSessionService examSessionService,
                                 ExamSnapshotService examSnapshotService,
                                 SubmissionMapper submissionMapper,
                                 SubmissionAnswerMapper submissionAnswerMapper,
                                 PaperQuestionMapper paperQuestionMapper,
                                 QuestionMapper questionMapper) {
        this.examAccessService = examAccessService;
        this.examSessionService = examSessionService;
        this.examSnapshotService = examSnapshotService;
        this.submissionMapper = submissionMapper;
        this.submissionAnswerMapper = submissionAnswerMapper;
        this.paperQuestionMapper = paperQuestionMapper;
        this.questionMapper = questionMapper;
    }

    @Transactional
    public SubmitResultView submit(Long examId, SubmitExamRequest request) {
        Long studentId = examAccessService.getCurrentUserId();
        Exam exam = examAccessService.ensureExam(examId);
        examAccessService.checkStudentAccess(examId, studentId);

        ExamSession session = examSessionService.findLatestSession(examId, studentId);
        if (session == null || SessionStatus.SUBMITTED.name().equals(session.getStatus())) {
            throw new BusinessException("不可重复交卷");
        }

        Submission submission = examSessionService.ensureInProgressSubmission(examId, studentId);
        Map<Long, String> answerMap = request.getAnswers().stream()
            .collect(Collectors.toMap(AnswerPayload::getQuestionId, AnswerPayload::getAnswerText, (a, b) -> b));

        List<PaperQuestion> paperQuestions = paperQuestionMapper.selectList(
            new LambdaQueryWrapper<PaperQuestion>()
                .eq(PaperQuestion::getPaperId, exam.getPaperId())
                .orderByAsc(PaperQuestion::getSortOrder)
        );
        List<Long> questionIds = paperQuestions.stream().map(PaperQuestion::getQuestionId).toList();
        Map<Long, Question> questionMap = questionMapper.selectBatchIds(questionIds).stream()
            .collect(Collectors.toMap(Question::getId, item -> item));

        submissionAnswerMapper.delete(new LambdaQueryWrapper<SubmissionAnswer>()
            .eq(SubmissionAnswer::getSubmissionId, submission.getId()));

        int objectiveScore = 0;
        int subjectiveScore = 0;
        boolean hasShort = false;

        for (PaperQuestion link : paperQuestions) {
            Question question = questionMap.get(link.getQuestionId());
            String userAnswer = answerMap.getOrDefault(link.getQuestionId(), "");

            SubmissionAnswer item = new SubmissionAnswer();
            item.setSubmissionId(submission.getId());
            item.setQuestionId(link.getQuestionId());
            item.setAnswerText(userAnswer);
            item.setFinalAnswer(true);
            item.setSource("SUBMIT");

            if (question != null && AnswerJudgeUtil.isObjectiveType(question.getType())) {
                boolean correct = AnswerJudgeUtil.isCorrect(question.getType(), question.getAnswer(), userAnswer);
                item.setObjectiveCorrect(correct);
                item.setObjectiveScore(correct ? link.getScore() : 0);
                objectiveScore += correct ? link.getScore() : 0;
            } else {
                hasShort = true;
                item.setObjectiveCorrect(null);
                item.setObjectiveScore(0);
                item.setSubjectiveScore(null);
            }
            submissionAnswerMapper.insert(item);
        }

        submission.setObjectiveScore(objectiveScore);
        submission.setSubjectiveScore(subjectiveScore);
        submission.setTotalScore(objectiveScore + subjectiveScore);
        submission.setSubmittedAt(LocalDateTime.now());
        submission.setStatus(hasShort ? SubmissionStatus.SUBMITTED.name() : SubmissionStatus.GRADED.name());
        submission.setPassFlag(submission.getTotalScore() >= exam.getPassScore());
        submissionMapper.updateById(submission);

        LocalDateTime submittedAt = LocalDateTime.now();
        examSessionService.markSubmitted(session, submittedAt);
        examSnapshotService.clearSnapshot(examId, studentId);
        log.info("Exam submitted: examId={}, studentId={}, submissionId={}, status={}, totalScore={}",
            examId, studentId, submission.getId(), submission.getStatus(), submission.getTotalScore());

        return SubmitResultView.builder()
            .submissionId(submission.getId())
            .objectiveScore(submission.getObjectiveScore())
            .subjectiveScore(submission.getSubjectiveScore())
            .totalScore(submission.getTotalScore())
            .passFlag(submission.getPassFlag())
            .status(submission.getStatus())
            .build();
    }
}
