package com.ekusys.exam.question.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ekusys.exam.common.api.PageResponse;
import com.ekusys.exam.common.exception.BusinessException;
import com.ekusys.exam.common.security.SecurityUtils;
import com.ekusys.exam.question.dto.QuestionCreateRequest;
import com.ekusys.exam.question.dto.QuestionQueryRequest;
import com.ekusys.exam.question.dto.QuestionUpdateRequest;
import com.ekusys.exam.question.dto.QuestionView;
import com.ekusys.exam.repository.entity.Question;
import com.ekusys.exam.repository.mapper.QuestionMapper;
import org.springframework.stereotype.Service;

@Service
public class QuestionService {

    private final QuestionMapper questionMapper;

    public QuestionService(QuestionMapper questionMapper) {
        this.questionMapper = questionMapper;
    }

    public PageResponse<QuestionView> query(QuestionQueryRequest request) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<Question>()
            .eq(request.getSubjectId() != null, Question::getSubjectId, request.getSubjectId())
            .eq(request.getType() != null, Question::getType, request.getType() == null ? null : request.getType().name())
            .eq(request.getDifficulty() != null, Question::getDifficulty, request.getDifficulty() == null ? null : request.getDifficulty().name())
            .like(request.getKeyword() != null && !request.getKeyword().isBlank(), Question::getContent, request.getKeyword())
            .orderByDesc(Question::getCreateTime);
        Page<Question> page = questionMapper.selectPage(new Page<>(request.getPageNum(), request.getPageSize()), wrapper);

        return PageResponse.<QuestionView>builder()
            .pageNum(page.getCurrent())
            .pageSize(page.getSize())
            .total(page.getTotal())
            .records(page.getRecords().stream().map(this::toView).toList())
            .build();
    }

    public Long create(QuestionCreateRequest request) {
        Question question = new Question();
        fill(request, question);
        question.setCreatorId(SecurityUtils.getCurrentUserId());
        questionMapper.insert(question);
        return question.getId();
    }

    public void update(Long id, QuestionUpdateRequest request) {
        Question question = questionMapper.selectById(id);
        if (question == null) {
            throw new BusinessException("题目不存在");
        }
        fill(request, question);
        questionMapper.updateById(question);
    }

    public void delete(Long id) {
        questionMapper.deleteById(id);
    }

    public QuestionView getById(Long id) {
        Question question = questionMapper.selectById(id);
        if (question == null) {
            throw new BusinessException("题目不存在");
        }
        return toView(question);
    }

    private void fill(QuestionCreateRequest request, Question question) {
        question.setSubjectId(request.getSubjectId());
        question.setType(request.getType().name());
        question.setDifficulty(request.getDifficulty().name());
        question.setContent(request.getContent());
        question.setOptionsJson(request.getOptionsJson());
        question.setAnswer(request.getAnswer());
        question.setAnalysis(request.getAnalysis());
        question.setDefaultScore(request.getDefaultScore());
    }

    private void fill(QuestionUpdateRequest request, Question question) {
        question.setSubjectId(request.getSubjectId());
        question.setType(request.getType().name());
        question.setDifficulty(request.getDifficulty().name());
        question.setContent(request.getContent());
        question.setOptionsJson(request.getOptionsJson());
        question.setAnswer(request.getAnswer());
        question.setAnalysis(request.getAnalysis());
        question.setDefaultScore(request.getDefaultScore());
    }

    private QuestionView toView(Question q) {
        return QuestionView.builder()
            .id(q.getId())
            .subjectId(q.getSubjectId())
            .type(q.getType())
            .difficulty(q.getDifficulty())
            .content(q.getContent())
            .optionsJson(q.getOptionsJson())
            .answer(q.getAnswer())
            .analysis(q.getAnalysis())
            .defaultScore(q.getDefaultScore())
            .build();
    }
}
