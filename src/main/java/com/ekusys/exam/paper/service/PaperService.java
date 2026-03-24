package com.ekusys.exam.paper.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ekusys.exam.common.exception.BusinessException;
import com.ekusys.exam.common.security.SecurityUtils;
import com.ekusys.exam.paper.dto.AutoGeneratePaperRequest;
import com.ekusys.exam.paper.dto.AutoGenerateRule;
import com.ekusys.exam.paper.dto.ManualCreatePaperRequest;
import com.ekusys.exam.paper.dto.ManualPaperQuestion;
import com.ekusys.exam.paper.dto.PaperDetailView;
import com.ekusys.exam.paper.dto.PaperQuestionView;
import com.ekusys.exam.repository.entity.Paper;
import com.ekusys.exam.repository.entity.PaperQuestion;
import com.ekusys.exam.repository.entity.Question;
import com.ekusys.exam.repository.mapper.PaperMapper;
import com.ekusys.exam.repository.mapper.PaperQuestionMapper;
import com.ekusys.exam.repository.mapper.QuestionMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaperService {

    private final PaperMapper paperMapper;
    private final PaperQuestionMapper paperQuestionMapper;
    private final QuestionMapper questionMapper;

    public PaperService(PaperMapper paperMapper,
                        PaperQuestionMapper paperQuestionMapper,
                        QuestionMapper questionMapper) {
        this.paperMapper = paperMapper;
        this.paperQuestionMapper = paperQuestionMapper;
        this.questionMapper = questionMapper;
    }

    @Transactional
    public Long createManual(ManualCreatePaperRequest request) {
        List<Long> questionIds = request.getQuestions().stream().map(ManualPaperQuestion::getQuestionId).toList();
        List<Question> questions = questionMapper.selectBatchIds(questionIds);
        if (questions.size() != questionIds.size()) {
            throw new BusinessException("存在无效题目");
        }

        Paper paper = buildPaper(request.getName(), request.getSubjectId(), request.getDescription());
        paperMapper.insert(paper);

        int totalScore = 0;
        for (ManualPaperQuestion item : request.getQuestions()) {
            PaperQuestion pq = new PaperQuestion();
            pq.setPaperId(paper.getId());
            pq.setQuestionId(item.getQuestionId());
            pq.setScore(item.getScore());
            pq.setSortOrder(item.getSortOrder());
            paperQuestionMapper.insert(pq);
            totalScore += item.getScore();
        }
        paper.setTotalScore(totalScore);
        paperMapper.updateById(paper);
        return paper.getId();
    }

    @Transactional
    public Long autoGenerate(AutoGeneratePaperRequest request) {
        List<ManualPaperQuestion> selected = new ArrayList<>();
        Set<Long> picked = new HashSet<>();
        int sort = 1;

        for (AutoGenerateRule rule : request.getRules()) {
            List<Question> pool = questionMapper.selectList(new LambdaQueryWrapper<Question>()
                .eq(Question::getSubjectId, request.getSubjectId())
                .eq(Question::getType, rule.getType().name())
                .eq(Question::getDifficulty, rule.getDifficulty().name())
                .notIn(!picked.isEmpty(), Question::getId, picked)
                .last("order by rand() limit " + rule.getCount()));

            if (pool.size() < rule.getCount()) {
                throw new BusinessException("题库数量不足，无法满足自动组卷规则");
            }

            for (Question question : pool) {
                picked.add(question.getId());
                ManualPaperQuestion item = new ManualPaperQuestion();
                item.setQuestionId(question.getId());
                item.setScore(rule.getScore());
                item.setSortOrder(sort++);
                selected.add(item);
            }
        }

        ManualCreatePaperRequest manual = new ManualCreatePaperRequest();
        manual.setName(request.getName());
        manual.setSubjectId(request.getSubjectId());
        manual.setDescription(request.getDescription());
        manual.setQuestions(selected);
        return createManual(manual);
    }

    public PaperDetailView getDetail(Long paperId) {
        Paper paper = paperMapper.selectById(paperId);
        if (paper == null) {
            throw new BusinessException("试卷不存在");
        }
        List<PaperQuestion> links = paperQuestionMapper.selectList(
            new LambdaQueryWrapper<PaperQuestion>().eq(PaperQuestion::getPaperId, paperId).orderByAsc(PaperQuestion::getSortOrder)
        );
        List<Long> qIds = links.stream().map(PaperQuestion::getQuestionId).toList();
        Map<Long, Question> questionMap = questionMapper.selectBatchIds(qIds).stream()
            .collect(Collectors.toMap(Question::getId, q -> q));

        List<PaperQuestionView> questions = links.stream().map(link -> {
            Question q = questionMap.get(link.getQuestionId());
            return PaperQuestionView.builder()
                .questionId(link.getQuestionId())
                .type(q == null ? null : q.getType())
                .difficulty(q == null ? null : q.getDifficulty())
                .content(q == null ? null : q.getContent())
                .optionsJson(q == null ? null : q.getOptionsJson())
                .answer(q == null ? null : q.getAnswer())
                .score(link.getScore())
                .sortOrder(link.getSortOrder())
                .build();
        }).toList();

        return PaperDetailView.builder()
            .id(paper.getId())
            .name(paper.getName())
            .subjectId(paper.getSubjectId())
            .description(paper.getDescription())
            .totalScore(paper.getTotalScore())
            .questions(questions)
            .build();
    }

    private Paper buildPaper(String name, Long subjectId, String description) {
        Paper paper = new Paper();
        paper.setName(name);
        paper.setSubjectId(subjectId);
        paper.setDescription(description);
        paper.setTotalScore(0);
        paper.setTeacherId(SecurityUtils.getCurrentUserId());
        return paper;
    }
}
