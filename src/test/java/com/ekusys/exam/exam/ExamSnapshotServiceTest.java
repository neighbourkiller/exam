package com.ekusys.exam.exam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ekusys.exam.common.config.AppSnapshotProperties;
import com.ekusys.exam.common.security.SecurityUtils;
import com.ekusys.exam.exam.dto.AnswerPayload;
import com.ekusys.exam.exam.dto.SnapshotRequest;
import com.ekusys.exam.exam.service.ExamAccessService;
import com.ekusys.exam.exam.service.ExamSessionService;
import com.ekusys.exam.exam.service.ExamSnapshotService;
import com.ekusys.exam.repository.entity.Exam;
import com.ekusys.exam.repository.entity.ExamSession;
import com.ekusys.exam.repository.entity.StudentTeachingClass;
import com.ekusys.exam.repository.entity.Submission;
import com.ekusys.exam.repository.entity.SubmissionAnswer;
import com.ekusys.exam.repository.mapper.ExamMapper;
import com.ekusys.exam.repository.mapper.ExamSessionMapper;
import com.ekusys.exam.repository.mapper.ExamTargetClassMapper;
import com.ekusys.exam.repository.mapper.StudentTeachingClassMapper;
import com.ekusys.exam.repository.mapper.SubmissionAnswerMapper;
import com.ekusys.exam.repository.mapper.SubmissionMapper;
import com.ekusys.exam.repository.mapper.UserMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class ExamSnapshotServiceTest {

    @Mock
    private ExamMapper examMapper;

    @Mock
    private ExamTargetClassMapper examTargetClassMapper;

    @Mock
    private StudentTeachingClassMapper studentTeachingClassMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private ExamSessionMapper examSessionMapper;

    @Mock
    private SubmissionMapper submissionMapper;

    @Mock
    private SubmissionAnswerMapper submissionAnswerMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ExamSnapshotService snapshotService;

    @BeforeEach
    void setUp() {
        ExamAccessService accessService = new ExamAccessService(
            examMapper,
            examTargetClassMapper,
            studentTeachingClassMapper,
            userMapper
        );
        ExamSessionService sessionService = new ExamSessionService(examSessionMapper, submissionMapper);
        AppSnapshotProperties properties = new AppSnapshotProperties();
        properties.setTtlHours(48);
        snapshotService = new ExamSnapshotService(
            accessService,
            sessionService,
            properties,
            submissionMapper,
            submissionAnswerMapper,
            redisTemplate,
            new ObjectMapper()
        );
    }

    @Test
    void saveSnapshotShouldSetTtlRelativeToExamEndTime() {
        Exam exam = new Exam();
        exam.setId(1L);
        exam.setEndTime(LocalDateTime.now().plusHours(2));
        when(examMapper.selectById(1L)).thenReturn(exam);
        StudentTeachingClass relation = new StudentTeachingClass();
        relation.setTeachingClassId(99L);
        when(studentTeachingClassMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(relation));
        when(examTargetClassMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        ExamSession session = new ExamSession();
        session.setId(10L);
        session.setExamId(1L);
        session.setStudentId(100L);
        session.setStatus("ANSWERING");
        when(examSessionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(session);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        SnapshotRequest request = new SnapshotRequest();
        AnswerPayload payload = new AnswerPayload();
        payload.setQuestionId(11L);
        payload.setAnswerText("A");
        request.setAnswers(List.of(payload));

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getCurrentUserId).thenReturn(100L);
            snapshotService.saveSnapshot(1L, request);
        }

        verify(valueOperations).set(
            eq("exam:snapshot:1:100"),
            any(String.class),
            any(Duration.class)
        );
    }

    @Test
    void flushSnapshotShouldSkipCompletedSubmissionAndDeleteSnapshot() {
        Submission submission = new Submission();
        submission.setId(20L);
        submission.setExamId(1L);
        submission.setStudentId(100L);
        submission.setStatus("SUBMITTED");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("exam:snapshot:1:100")).thenReturn("{\"answers\":[]}");
        when(submissionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(submission);

        snapshotService.flushSnapshotKey("exam:snapshot:1:100");

        verify(redisTemplate).delete("exam:snapshot:1:100");
        verify(submissionAnswerMapper, never()).insert(org.mockito.ArgumentMatchers.<SubmissionAnswer>any());
    }

    @Test
    void loadSnapshotAnswerMapShouldMergePersistedDraftAndRedisSnapshot() {
        Submission submission = new Submission();
        submission.setId(20L);
        submission.setExamId(1L);
        submission.setStudentId(100L);
        submission.setStatus("IN_PROGRESS");
        when(submissionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(submission);

        SubmissionAnswer persisted = new SubmissionAnswer();
        persisted.setSubmissionId(20L);
        persisted.setQuestionId(11L);
        persisted.setAnswerText("A");
        persisted.setFinalAnswer(false);
        when(submissionAnswerMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(persisted));

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("exam:snapshot:1:100")).thenReturn(
            "{\"answers\":[{\"questionId\":12,\"answerText\":\"B\"},{\"questionId\":11,\"answerText\":\"C\"}]}"
        );

        Map<Long, String> answerMap = snapshotService.loadSnapshotAnswerMap(1L, 100L);

        assertEquals(2, answerMap.size());
        assertEquals("C", answerMap.get(11L));
        assertEquals("B", answerMap.get(12L));
    }
}

