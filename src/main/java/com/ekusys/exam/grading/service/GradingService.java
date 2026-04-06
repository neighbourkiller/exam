package com.ekusys.exam.grading.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ekusys.exam.common.enums.SubmissionStatus;
import com.ekusys.exam.common.exception.BusinessException;
import com.ekusys.exam.common.security.SecurityUtils;
import com.ekusys.exam.common.util.AnswerJudgeUtil;
import com.ekusys.exam.exam.service.ExamPermissionService;
import com.ekusys.exam.grading.dto.PendingAnswerView;
import com.ekusys.exam.grading.dto.SubjectiveScoreItem;
import com.ekusys.exam.grading.dto.SubjectiveScoreRequest;
import com.ekusys.exam.repository.entity.Exam;
import com.ekusys.exam.repository.entity.Question;
import com.ekusys.exam.repository.entity.Submission;
import com.ekusys.exam.repository.entity.SubmissionAnswer;
import com.ekusys.exam.repository.entity.SubjectiveGrade;
import com.ekusys.exam.repository.entity.User;
import com.ekusys.exam.repository.mapper.ExamMapper;
import com.ekusys.exam.repository.mapper.QuestionMapper;
import com.ekusys.exam.repository.mapper.SubmissionAnswerMapper;
import com.ekusys.exam.repository.mapper.SubmissionMapper;
import com.ekusys.exam.repository.mapper.SubjectiveGradeMapper;
import com.ekusys.exam.repository.mapper.UserMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GradingService {

    private final SubmissionMapper submissionMapper;
    private final SubmissionAnswerMapper submissionAnswerMapper;
    private final QuestionMapper questionMapper;
    private final SubjectiveGradeMapper subjectiveGradeMapper;
    private final ExamMapper examMapper;
    private final UserMapper userMapper;
    private final ExamPermissionService examPermissionService;

    public GradingService(SubmissionMapper submissionMapper,
                          SubmissionAnswerMapper submissionAnswerMapper,
                          QuestionMapper questionMapper,
                          SubjectiveGradeMapper subjectiveGradeMapper,
                          ExamMapper examMapper,
                          UserMapper userMapper,
                          ExamPermissionService examPermissionService) {
        this.submissionMapper = submissionMapper;
        this.submissionAnswerMapper = submissionAnswerMapper;
        this.questionMapper = questionMapper;
        this.subjectiveGradeMapper = subjectiveGradeMapper;
        this.examMapper = examMapper;
        this.userMapper = userMapper;
        this.examPermissionService = examPermissionService;
    }

    public List<PendingAnswerView> pendingAnswers() {
        List<Submission> submissions = submissionMapper.selectList(
            new LambdaQueryWrapper<Submission>().eq(Submission::getStatus, SubmissionStatus.SUBMITTED.name())
        );
        if (submissions.isEmpty()) {
            return List.of();
        }

        Set<Long> examIds = submissions.stream().map(Submission::getExamId).collect(Collectors.toSet());

        Map<Long, Exam> examMap = examIds.isEmpty()
            ? Map.of()
            : examMapper.selectBatchIds(examIds).stream().collect(Collectors.toMap(Exam::getId, exam -> exam, (a, b) -> a));
        Set<Long> manageableExamIds = examPermissionService.filterManageableExams(examMap.values()).stream()
            .map(Exam::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (manageableExamIds.isEmpty()) {
            return List.of();
        }
        submissions = submissions.stream()
            .filter(item -> manageableExamIds.contains(item.getExamId()))
            .toList();
        if (submissions.isEmpty()) {
            return List.of();
        }

        Set<Long> studentIds = submissions.stream().map(Submission::getStudentId).collect(Collectors.toSet());
        Map<Long, User> userMap = studentIds.isEmpty()
            ? Map.of()
            : userMapper.selectBatchIds(studentIds).stream().collect(Collectors.toMap(User::getId, user -> user, (a, b) -> a));

        List<Long> submissionIds = submissions.stream().map(Submission::getId).toList();
        List<SubmissionAnswer> answers = submissionAnswerMapper.selectList(
            new LambdaQueryWrapper<SubmissionAnswer>().in(SubmissionAnswer::getSubmissionId, submissionIds)
        );
        Map<Long, List<SubmissionAnswer>> answersBySubmission = answers.stream()
            .collect(Collectors.groupingBy(SubmissionAnswer::getSubmissionId));

        Set<Long> questionIds = answers.stream()
            .map(SubmissionAnswer::getQuestionId)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(HashSet::new));
        Map<Long, Question> questionMap = questionIds.isEmpty()
            ? Map.of()
            : questionMapper.selectBatchIds(questionIds).stream().collect(Collectors.toMap(Question::getId, question -> question, (a, b) -> a));

        List<PendingAnswerView> result = new ArrayList<>();
        for (Submission submission : submissions) {
            List<SubmissionAnswer> submissionAnswers = answersBySubmission.getOrDefault(submission.getId(), List.of());
            Exam exam = examMap.get(submission.getExamId());
            User student = userMap.get(submission.getStudentId());
            for (SubmissionAnswer answer : submissionAnswers) {
                Question question = questionMap.get(answer.getQuestionId());
                if (question == null || !"SHORT".equals(question.getType()) || answer.getSubjectiveScore() != null) {
                    continue;
                }
                result.add(PendingAnswerView.builder()
                    .submissionId(submission.getId())
                    .submissionAnswerId(answer.getId())
                    .examId(submission.getExamId())
                    .examName(exam == null ? "" : exam.getName())
                    .studentId(submission.getStudentId())
                    .studentName(student == null ? "" : student.getRealName())
                    .questionId(answer.getQuestionId())
                    .questionContent(question.getContent())
                    .answerText(answer.getAnswerText())
                    .build());
            }
        }
        return result;
    }

    @Transactional
    public void scoreSubjective(Long submissionId, SubjectiveScoreRequest request) {
        Submission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new BusinessException("提交记录不存在");
        }
        Exam exam = examMapper.selectById(submission.getExamId());
        examPermissionService.ensureCanManageExam(exam, "无权限批阅该考试");

        List<Long> scoreAnswerIds = request.getScores().stream().map(SubjectiveScoreItem::getSubmissionAnswerId).toList();
        Map<Long, SubmissionAnswer> answerMap = submissionAnswerMapper.selectBatchIds(scoreAnswerIds).stream()
            .collect(Collectors.toMap(SubmissionAnswer::getId, answer -> answer, (a, b) -> a));
        Set<Long> questionIds = answerMap.values().stream()
            .map(SubmissionAnswer::getQuestionId)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(HashSet::new));
        Map<Long, Question> questionMap = questionIds.isEmpty()
            ? Map.of()
            : questionMapper.selectBatchIds(questionIds).stream().collect(Collectors.toMap(Question::getId, question -> question, (a, b) -> a));

        for (SubjectiveScoreItem score : request.getScores()) {
            SubmissionAnswer answer = answerMap.get(score.getSubmissionAnswerId());
            if (answer == null || !submissionId.equals(answer.getSubmissionId())) {
                throw new BusinessException("主观题记录不存在");
            }
            Question question = questionMap.get(answer.getQuestionId());
            if (question == null || !"SHORT".equals(question.getType())) {
                throw new BusinessException("仅允许批阅简答题");
            }

            answer.setSubjectiveScore(score.getScore());
            submissionAnswerMapper.updateById(answer);

            SubjectiveGrade grade = new SubjectiveGrade();
            grade.setSubmissionAnswerId(answer.getId());
            grade.setTeacherId(SecurityUtils.getCurrentUserId());
            grade.setScore(score.getScore());
            grade.setComment(score.getComment());
            grade.setGradedAt(LocalDateTime.now());
            subjectiveGradeMapper.insert(grade);
        }

        List<SubmissionAnswer> answers = submissionAnswerMapper.selectList(
            new LambdaQueryWrapper<SubmissionAnswer>().eq(SubmissionAnswer::getSubmissionId, submissionId)
        );
        Set<Long> allQuestionIds = answers.stream()
            .map(SubmissionAnswer::getQuestionId)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(HashSet::new));
        Map<Long, Question> allQuestionMap = allQuestionIds.isEmpty()
            ? Map.of()
            : questionMapper.selectBatchIds(allQuestionIds).stream().collect(Collectors.toMap(Question::getId, question -> question, (a, b) -> a));

        int objective = answers.stream().map(SubmissionAnswer::getObjectiveScore).filter(v -> v != null).mapToInt(Integer::intValue).sum();
        int subjective = answers.stream().map(SubmissionAnswer::getSubjectiveScore).filter(v -> v != null).mapToInt(Integer::intValue).sum();
        boolean allShortScored = answers.stream().allMatch(answer -> {
            Question q = allQuestionMap.get(answer.getQuestionId());
            return q == null || !"SHORT".equals(q.getType()) || answer.getSubjectiveScore() != null;
        });

        submission.setObjectiveScore(objective);
        submission.setSubjectiveScore(subjective);
        submission.setTotalScore(objective + subjective);
        submission.setPassFlag(exam != null && submission.getTotalScore() >= exam.getPassScore());
        submission.setStatus(allShortScored ? SubmissionStatus.GRADED.name() : SubmissionStatus.SUBMITTED.name());
        submissionMapper.updateById(submission);
    }
}
