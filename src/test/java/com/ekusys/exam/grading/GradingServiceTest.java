package com.ekusys.exam.grading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ekusys.exam.common.exception.BusinessException;
import com.ekusys.exam.common.security.SecurityUtils;
import com.ekusys.exam.exam.service.ExamPermissionService;
import com.ekusys.exam.grading.dto.PendingAnswerView;
import com.ekusys.exam.grading.dto.SubjectiveScoreItem;
import com.ekusys.exam.grading.dto.SubjectiveScoreRequest;
import com.ekusys.exam.grading.service.GradingService;
import com.ekusys.exam.repository.entity.Exam;
import com.ekusys.exam.repository.entity.Question;
import com.ekusys.exam.repository.entity.Submission;
import com.ekusys.exam.repository.entity.SubmissionAnswer;
import com.ekusys.exam.repository.entity.User;
import com.ekusys.exam.repository.mapper.ExamMapper;
import com.ekusys.exam.repository.mapper.ExamTargetClassMapper;
import com.ekusys.exam.repository.mapper.QuestionMapper;
import com.ekusys.exam.repository.mapper.SubmissionAnswerMapper;
import com.ekusys.exam.repository.mapper.SubmissionMapper;
import com.ekusys.exam.repository.mapper.SubjectiveGradeMapper;
import com.ekusys.exam.repository.mapper.TeachingClassMapper;
import com.ekusys.exam.repository.mapper.UserMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GradingServiceTest {

    @Mock
    private SubmissionMapper submissionMapper;

    @Mock
    private SubmissionAnswerMapper submissionAnswerMapper;

    @Mock
    private QuestionMapper questionMapper;

    @Mock
    private SubjectiveGradeMapper subjectiveGradeMapper;

    @Mock
    private ExamMapper examMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private ExamTargetClassMapper examTargetClassMapper;

    @Mock
    private TeachingClassMapper teachingClassMapper;

    private GradingService gradingService;

    @BeforeEach
    void setUp() {
        ExamPermissionService examPermissionService = new ExamPermissionService(
            userMapper,
            examTargetClassMapper,
            teachingClassMapper
        );
        gradingService = new GradingService(
            submissionMapper,
            submissionAnswerMapper,
            questionMapper,
            subjectiveGradeMapper,
            examMapper,
            userMapper,
            examPermissionService
        );
    }

    @Test
    void pendingAnswersShouldOnlyReturnAccessibleExamRecords() {
        when(userMapper.selectRoleCodes(200L)).thenReturn(List.of("TEACHER"));
        when(submissionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
            submission(1L, 101L, 1001L),
            submission(2L, 102L, 1002L)
        ));
        when(examMapper.selectBatchIds(any())).thenReturn(List.of(
            exam(101L, 200L),
            exam(102L, 999L)
        ));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(
            user(1001L, "Alice"),
            user(1002L, "Bob")
        ));
        when(submissionAnswerMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
            answer(11L, 1L, 501L, "答案A"),
            answer(12L, 2L, 502L, "答案B")
        ));
        when(questionMapper.selectBatchIds(any())).thenReturn(List.of(
            shortQuestion(501L, "题目A"),
            shortQuestion(502L, "题目B")
        ));

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getCurrentUserId).thenReturn(200L);

            List<PendingAnswerView> result = gradingService.pendingAnswers();

            assertEquals(1, result.size());
            assertEquals(101L, result.get(0).getExamId());
            assertEquals("Alice", result.get(0).getStudentName());
        }
    }

    @Test
    void scoreSubjectiveShouldRejectUnauthorizedExam() {
        when(userMapper.selectRoleCodes(200L)).thenReturn(List.of("TEACHER"));
        when(submissionMapper.selectById(1L)).thenReturn(submission(1L, 102L, 1002L));
        when(examMapper.selectById(102L)).thenReturn(exam(102L, 999L));
        when(examTargetClassMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        SubjectiveScoreRequest request = new SubjectiveScoreRequest();
        SubjectiveScoreItem item = new SubjectiveScoreItem();
        item.setSubmissionAnswerId(12L);
        item.setScore(8);
        request.setScores(List.of(item));

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getCurrentUserId).thenReturn(200L);
            assertThrows(BusinessException.class, () -> gradingService.scoreSubjective(1L, request));
        }
    }

    private Submission submission(Long id, Long examId, Long studentId) {
        Submission submission = new Submission();
        submission.setId(id);
        submission.setExamId(examId);
        submission.setStudentId(studentId);
        submission.setStatus("SUBMITTED");
        return submission;
    }

    private Exam exam(Long id, Long publisherId) {
        Exam exam = new Exam();
        exam.setId(id);
        exam.setName("exam-" + id);
        exam.setPublisherId(publisherId);
        return exam;
    }

    private User user(Long id, String realName) {
        User user = new User();
        user.setId(id);
        user.setRealName(realName);
        return user;
    }

    private SubmissionAnswer answer(Long id, Long submissionId, Long questionId, String answerText) {
        SubmissionAnswer answer = new SubmissionAnswer();
        answer.setId(id);
        answer.setSubmissionId(submissionId);
        answer.setQuestionId(questionId);
        answer.setAnswerText(answerText);
        return answer;
    }

    private Question shortQuestion(Long id, String content) {
        Question question = new Question();
        question.setId(id);
        question.setType("SHORT");
        question.setContent(content);
        return question;
    }
}
