package com.ekusys.exam.grading.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ekusys.exam.common.enums.SubmissionStatus;
import com.ekusys.exam.common.exception.BusinessException;
import com.ekusys.exam.common.security.SecurityUtils;
import com.ekusys.exam.common.util.AnswerJudgeUtil;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    public GradingService(SubmissionMapper submissionMapper,
                          SubmissionAnswerMapper submissionAnswerMapper,
                          QuestionMapper questionMapper,
                          SubjectiveGradeMapper subjectiveGradeMapper,
                          ExamMapper examMapper,
                          UserMapper userMapper) {
        this.submissionMapper = submissionMapper;
        this.submissionAnswerMapper = submissionAnswerMapper;
        this.questionMapper = questionMapper;
        this.subjectiveGradeMapper = subjectiveGradeMapper;
        this.examMapper = examMapper;
        this.userMapper = userMapper;
    }

    public List<PendingAnswerView> pendingAnswers() {
        List<Submission> submissions = submissionMapper.selectList(
            new LambdaQueryWrapper<Submission>().eq(Submission::getStatus, SubmissionStatus.SUBMITTED.name())
        );
        if (submissions.isEmpty()) {
            return List.of();
        }

        Map<Long, Exam> examMap = new HashMap<>();
        Map<Long, User> userMap = new HashMap<>();
        new java.util.HashSet<>(submissions.stream().map(Submission::getExamId).toList())
            .forEach(id -> examMap.put(id, examMapper.selectById(id)));
        new java.util.HashSet<>(submissions.stream().map(Submission::getStudentId).toList())
            .forEach(id -> userMap.put(id, userMapper.selectById(id)));

        return submissions.stream().flatMap(submission -> {
            List<SubmissionAnswer> answers = submissionAnswerMapper.selectList(new LambdaQueryWrapper<SubmissionAnswer>()
                .eq(SubmissionAnswer::getSubmissionId, submission.getId()));
            return answers.stream().map(answer -> {
                Question q = questionMapper.selectById(answer.getQuestionId());
                if (q == null || !"SHORT".equals(q.getType())) {
                    return null;
                }
                if (answer.getSubjectiveScore() != null) {
                    return null;
                }
                Exam exam = examMap.get(submission.getExamId());
                User student = userMap.get(submission.getStudentId());
                return PendingAnswerView.builder()
                    .submissionId(submission.getId())
                    .submissionAnswerId(answer.getId())
                    .examId(submission.getExamId())
                    .examName(exam == null ? "" : exam.getName())
                    .studentId(submission.getStudentId())
                    .studentName(student == null ? "" : student.getRealName())
                    .questionId(answer.getQuestionId())
                    .questionContent(q.getContent())
                    .answerText(answer.getAnswerText())
                    .build();
            }).filter(v -> v != null);
        }).toList();
    }

    @Transactional
    public void scoreSubjective(Long submissionId, SubjectiveScoreRequest request) {
        Submission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new BusinessException("提交记录不存在");
        }

        for (SubjectiveScoreItem score : request.getScores()) {
            SubmissionAnswer answer = submissionAnswerMapper.selectById(score.getSubmissionAnswerId());
            if (answer == null || !submissionId.equals(answer.getSubmissionId())) {
                throw new BusinessException("主观题记录不存在");
            }
            Question question = questionMapper.selectById(answer.getQuestionId());
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

        int objective = answers.stream().map(SubmissionAnswer::getObjectiveScore).filter(v -> v != null).mapToInt(Integer::intValue).sum();
        int subjective = answers.stream().map(SubmissionAnswer::getSubjectiveScore).filter(v -> v != null).mapToInt(Integer::intValue).sum();
        boolean allShortScored = answers.stream().allMatch(answer -> {
            Question q = questionMapper.selectById(answer.getQuestionId());
            return q == null || !"SHORT".equals(q.getType()) || answer.getSubjectiveScore() != null;
        });

        submission.setObjectiveScore(objective);
        submission.setSubjectiveScore(subjective);
        submission.setTotalScore(objective + subjective);
        Exam exam = examMapper.selectById(submission.getExamId());
        submission.setPassFlag(exam != null && submission.getTotalScore() >= exam.getPassScore());
        submission.setStatus(allShortScored ? SubmissionStatus.GRADED.name() : SubmissionStatus.SUBMITTED.name());
        submissionMapper.updateById(submission);
    }
}
