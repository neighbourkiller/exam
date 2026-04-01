package com.ekusys.exam.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ekusys.exam.analytics.dto.ExamOverviewItem;
import com.ekusys.exam.analytics.dto.WrongTopicItem;
import com.ekusys.exam.analytics.service.AnalyticsService;
import com.ekusys.exam.repository.entity.Exam;
import com.ekusys.exam.repository.entity.Question;
import com.ekusys.exam.repository.entity.Submission;
import com.ekusys.exam.repository.entity.SubmissionAnswer;
import com.ekusys.exam.repository.mapper.ExamMapper;
import com.ekusys.exam.repository.mapper.PaperMapper;
import com.ekusys.exam.repository.mapper.QuestionMapper;
import com.ekusys.exam.repository.mapper.StudentTeachingClassMapper;
import com.ekusys.exam.repository.mapper.SubmissionAnswerMapper;
import com.ekusys.exam.repository.mapper.SubmissionMapper;
import com.ekusys.exam.repository.mapper.TeachingClassMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private SubmissionMapper submissionMapper;

    @Mock
    private SubmissionAnswerMapper submissionAnswerMapper;

    @Mock
    private ExamMapper examMapper;

    @Mock
    private PaperMapper paperMapper;

    @Mock
    private StudentTeachingClassMapper studentTeachingClassMapper;

    @Mock
    private TeachingClassMapper teachingClassMapper;

    @Mock
    private QuestionMapper questionMapper;

    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        analyticsService = new AnalyticsService(
            submissionMapper,
            submissionAnswerMapper,
            examMapper,
            paperMapper,
            studentTeachingClassMapper,
            teachingClassMapper,
            questionMapper
        );
    }

    @Test
    void overviewShouldAggregateBasicMetrics() {
        Exam exam = new Exam();
        exam.setId(100L);
        exam.setPassScore(60);
        when(examMapper.selectById(100L)).thenReturn(exam);

        when(submissionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
            submission(1L, 55),
            submission(2L, 60),
            submission(3L, 90),
            submission(4L, null)
        ));

        ExamOverviewItem overview = analyticsService.overview(100L);

        assertEquals(4, overview.getTotalStudents());
        assertEquals(2, overview.getPassCount());
        assertEquals(50.0, overview.getPassRate());
        assertEquals(51.25, overview.getAvgScore());
        assertEquals(90, overview.getMaxScore());
        assertEquals(0, overview.getMinScore());
    }

    @Test
    void overviewShouldReturnEmptyMetricsWhenNoSubmission() {
        when(submissionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        ExamOverviewItem overview = analyticsService.overview(100L);

        assertEquals(0, overview.getTotalStudents());
        assertEquals(0, overview.getPassCount());
        assertEquals(0.0, overview.getPassRate());
        assertEquals(0.0, overview.getAvgScore());
        assertNull(overview.getMaxScore());
        assertNull(overview.getMinScore());
    }

    @Test
    void wrongTopicsShouldSortByWrongRateAndRespectTopN() {
        when(submissionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
            submission(1L, 60),
            submission(2L, 70)
        ));
        when(submissionAnswerMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
            answer(1L, 10L, false),
            answer(2L, 10L, false),
            answer(1L, 20L, false),
            answer(2L, 20L, true),
            answer(1L, 30L, true),
            answer(2L, 30L, true)
        ));
        when(questionMapper.selectBatchIds(any())).thenReturn(List.of(
            question(10L, "Q10"),
            question(20L, "Q20"),
            question(30L, "Q30")
        ));

        List<WrongTopicItem> items = analyticsService.wrongTopics(100L, 2);

        assertEquals(2, items.size());
        assertEquals(10L, items.get(0).getQuestionId());
        assertEquals(100.0, items.get(0).getWrongRate());
        assertEquals(20L, items.get(1).getQuestionId());
        assertEquals(50.0, items.get(1).getWrongRate());
    }

    @Test
    void wrongTopicsShouldClampTopNToMinimum() {
        when(submissionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
            submission(1L, 60),
            submission(2L, 70)
        ));
        when(submissionAnswerMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
            answer(1L, 10L, false),
            answer(2L, 10L, false),
            answer(1L, 20L, false),
            answer(2L, 20L, true)
        ));
        when(questionMapper.selectBatchIds(any())).thenReturn(List.of(
            question(10L, "Q10"),
            question(20L, "Q20")
        ));

        List<WrongTopicItem> items = analyticsService.wrongTopics(100L, 0);

        assertEquals(1, items.size());
        assertEquals(10L, items.get(0).getQuestionId());
    }

    private Submission submission(Long id, Integer totalScore) {
        Submission submission = new Submission();
        submission.setId(id);
        submission.setExamId(100L);
        submission.setStudentId(2000L + id);
        submission.setTotalScore(totalScore);
        return submission;
    }

    private SubmissionAnswer answer(Long submissionId, Long questionId, Boolean objectiveCorrect) {
        SubmissionAnswer answer = new SubmissionAnswer();
        answer.setSubmissionId(submissionId);
        answer.setQuestionId(questionId);
        answer.setObjectiveCorrect(objectiveCorrect);
        return answer;
    }

    private Question question(Long id, String content) {
        Question question = new Question();
        question.setId(id);
        question.setContent(content);
        return question;
    }
}
