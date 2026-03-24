package com.ekusys.exam.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ekusys.exam.common.enums.Difficulty;
import com.ekusys.exam.common.enums.QuestionType;
import com.ekusys.exam.common.exception.BusinessException;
import com.ekusys.exam.paper.dto.AutoGeneratePaperRequest;
import com.ekusys.exam.paper.dto.AutoGenerateRule;
import com.ekusys.exam.paper.service.PaperService;
import com.ekusys.exam.repository.entity.Paper;
import com.ekusys.exam.repository.entity.Question;
import com.ekusys.exam.repository.mapper.PaperMapper;
import com.ekusys.exam.repository.mapper.PaperQuestionMapper;
import com.ekusys.exam.repository.mapper.QuestionMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaperServiceTest {

    @Mock
    private PaperMapper paperMapper;

    @Mock
    private PaperQuestionMapper paperQuestionMapper;

    @Mock
    private QuestionMapper questionMapper;

    private PaperService paperService;

    @BeforeEach
    void setUp() {
        paperService = new PaperService(paperMapper, paperQuestionMapper, questionMapper);
        lenient().doAnswer(invocation -> {
            Paper paper = invocation.getArgument(0);
            paper.setId(9001L);
            return 1;
        }).when(paperMapper).insert(any(Paper.class));
    }

    @Test
    void autoGenerateShouldCreatePaperWhenPoolEnough() {
        AutoGenerateRule rule = new AutoGenerateRule();
        rule.setType(QuestionType.SINGLE);
        rule.setDifficulty(Difficulty.EASY);
        rule.setCount(2);
        rule.setScore(5);

        AutoGeneratePaperRequest request = new AutoGeneratePaperRequest();
        request.setName("自动卷");
        request.setSubjectId(5001L);
        request.setDescription("desc");
        request.setRules(List.of(rule));

        Question q1 = new Question();
        q1.setId(11L);
        Question q2 = new Question();
        q2.setId(12L);

        when(questionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(q1, q2));
        when(questionMapper.selectBatchIds(List.of(11L, 12L))).thenReturn(List.of(q1, q2));

        Long paperId = paperService.autoGenerate(request);
        assertEquals(9001L, paperId);
    }

    @Test
    void autoGenerateShouldFailWhenPoolNotEnough() {
        AutoGenerateRule rule = new AutoGenerateRule();
        rule.setType(QuestionType.SINGLE);
        rule.setDifficulty(Difficulty.EASY);
        rule.setCount(3);
        rule.setScore(5);

        AutoGeneratePaperRequest request = new AutoGeneratePaperRequest();
        request.setName("自动卷");
        request.setSubjectId(5001L);
        request.setDescription("desc");
        request.setRules(List.of(rule));

        Question q1 = new Question();
        q1.setId(11L);
        when(questionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(q1));

        assertThrows(BusinessException.class, () -> paperService.autoGenerate(request));
    }
}
