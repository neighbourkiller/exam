package com.ekusys.exam.question.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ekusys.exam.common.api.PageResponse;
import com.ekusys.exam.common.exception.BusinessException;
import com.ekusys.exam.common.security.SecurityUtils;
import com.ekusys.exam.question.dto.QuestionCreateRequest;
import com.ekusys.exam.question.dto.QuestionQueryRequest;
import com.ekusys.exam.question.dto.QuestionSubjectOptionView;
import com.ekusys.exam.question.dto.QuestionView;
import com.ekusys.exam.repository.entity.Question;
import com.ekusys.exam.repository.entity.QuestionAsset;
import com.ekusys.exam.repository.entity.Subject;
import com.ekusys.exam.repository.mapper.QuestionAssetMapper;
import com.ekusys.exam.repository.mapper.QuestionMapper;
import com.ekusys.exam.repository.mapper.SubjectMapper;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuestionService {

    private final QuestionMapper questionMapper;
    private final SubjectMapper subjectMapper;
    private final QuestionAssetMapper questionAssetMapper;

    public QuestionService(QuestionMapper questionMapper,
                           SubjectMapper subjectMapper,
                           QuestionAssetMapper questionAssetMapper) {
        this.questionMapper = questionMapper;
        this.subjectMapper = subjectMapper;
        this.questionAssetMapper = questionAssetMapper;
    }

    public PageResponse<QuestionView> query(QuestionQueryRequest request) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<Question>()
            .eq(request.getSubjectId() != null, Question::getSubjectId, request.getSubjectId())
            .eq(request.getType() != null, Question::getType, request.getType() == null ? null : request.getType().name())
            .eq(request.getDifficulty() != null, Question::getDifficulty, request.getDifficulty() == null ? null : request.getDifficulty().name())
            .like(request.getKeyword() != null && !request.getKeyword().isBlank(), Question::getContent, request.getKeyword())
            .orderByDesc(Question::getCreateTime);
        Page<Question> page = questionMapper.selectPage(new Page<>(request.getPageNum(), request.getPageSize()), wrapper);
        Map<Long, String> subjectNameMap = buildSubjectNameMap(page.getRecords());

        return PageResponse.<QuestionView>builder()
            .pageNum(page.getCurrent())
            .pageSize(page.getSize())
            .total(page.getTotal())
            .records(page.getRecords().stream().map(q -> toView(q, subjectNameMap)).toList())
            .build();
    }

    public List<QuestionSubjectOptionView> listSubjectOptions() {
        return subjectMapper.selectList(new LambdaQueryWrapper<Subject>().orderByAsc(Subject::getId)).stream()
            .map(subject -> QuestionSubjectOptionView.builder()
                .id(subject.getId())
                .name(subject.getName())
                .build())
            .toList();
    }

    @Transactional
    public Long create(QuestionCreateRequest request) {
        Question question = new Question();
        fill(request, question);
        question.setCreatorId(SecurityUtils.getCurrentUserId());
        questionMapper.insert(question);
        bindAssetsToQuestion(question.getId(), request.getAssetIds());
        return question.getId();
    }

    public void update(Long id) {
        Question question = questionMapper.selectById(id);
        if (question == null) {
            throw new BusinessException("题目不存在");
        }
        ensureManagePermission(question);
    }

    @Transactional
    public void delete(Long id) {
        Question question = questionMapper.selectById(id);
        if (question == null) {
            throw new BusinessException("题目不存在");
        }
        ensureManagePermission(question);
        questionAssetMapper.delete(new LambdaQueryWrapper<QuestionAsset>().eq(QuestionAsset::getQuestionId, id));
        questionMapper.deleteById(id);
    }

    public QuestionView getById(Long id) {
        Question question = questionMapper.selectById(id);
        if (question == null) {
            throw new BusinessException("题目不存在");
        }
        Map<Long, String> subjectNameMap = buildSubjectNameMap(List.of(question));
        return toView(question, subjectNameMap);
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

    private QuestionView toView(Question q, Map<Long, String> subjectNameMap) {
        return QuestionView.builder()
            .id(q.getId())
            .subjectId(q.getSubjectId())
            .subjectName(subjectNameMap.get(q.getSubjectId()))
            .type(q.getType())
            .difficulty(q.getDifficulty())
            .content(q.getContent())
            .optionsJson(q.getOptionsJson())
            .answer(q.getAnswer())
            .analysis(q.getAnalysis())
            .defaultScore(q.getDefaultScore())
            .build();
    }

    private Map<Long, String> buildSubjectNameMap(List<Question> questions) {
        Set<Long> subjectIds = questions.stream()
            .map(Question::getSubjectId)
            .filter(id -> id != null)
            .collect(Collectors.toSet());
        if (subjectIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return subjectMapper.selectList(new LambdaQueryWrapper<Subject>().in(Subject::getId, subjectIds)).stream()
            .collect(Collectors.toMap(Subject::getId, Subject::getName, (a, b) -> a));
    }

    private void ensureManagePermission(Question question) {
        if (isCurrentUserAdmin()) {
            return;
        }
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null || question.getCreatorId() == null || !currentUserId.equals(question.getCreatorId())) {
            throw new BusinessException("无权限操作他人题目");
        }
    }

    private boolean isCurrentUserAdmin() {
        return SecurityUtils.getCurrentRoles().contains("ADMIN");
    }

    private void bindAssetsToQuestion(Long questionId, List<Long> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            return;
        }
        List<Long> uniqueIds = assetIds.stream().filter(id -> id != null).distinct().toList();
        if (uniqueIds.isEmpty()) {
            return;
        }

        List<QuestionAsset> assets = questionAssetMapper.selectList(
            new LambdaQueryWrapper<QuestionAsset>().in(QuestionAsset::getId, uniqueIds)
        );
        if (assets.size() != uniqueIds.size()) {
            throw new BusinessException("存在无效的附件ID");
        }

        Long currentUserId = SecurityUtils.getCurrentUserId();
        boolean admin = isCurrentUserAdmin();
        for (QuestionAsset asset : assets) {
            if (!admin && (currentUserId == null || !currentUserId.equals(asset.getUploaderId()))) {
                throw new BusinessException("无权绑定他人上传的附件");
            }
            if (asset.getQuestionId() != null && !questionId.equals(asset.getQuestionId())) {
                throw new BusinessException("附件已绑定到其他题目");
            }
            questionAssetMapper.update(
                null,
                new LambdaUpdateWrapper<QuestionAsset>()
                    .eq(QuestionAsset::getId, asset.getId())
                    .set(QuestionAsset::getQuestionId, questionId)
            );
        }
    }
}
