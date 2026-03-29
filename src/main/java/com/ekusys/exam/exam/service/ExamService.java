package com.ekusys.exam.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ekusys.exam.common.enums.ExamStatus;
import com.ekusys.exam.common.enums.SessionStatus;
import com.ekusys.exam.common.enums.SubmissionStatus;
import com.ekusys.exam.common.exception.BusinessException;
import com.ekusys.exam.common.security.SecurityUtils;
import com.ekusys.exam.common.util.AnswerJudgeUtil;
import com.ekusys.exam.exam.dto.AnswerPayload;
import com.ekusys.exam.exam.dto.AntiCheatEventRequest;
import com.ekusys.exam.exam.dto.ExamCreateRequest;
import com.ekusys.exam.exam.dto.SnapshotRequest;
import com.ekusys.exam.exam.dto.StartExamResponse;
import com.ekusys.exam.exam.dto.StudentExamQuestionView;
import com.ekusys.exam.exam.dto.StudentExamView;
import com.ekusys.exam.exam.dto.TeachingClassOptionView;
import com.ekusys.exam.exam.dto.SubmitExamRequest;
import com.ekusys.exam.exam.dto.SubmitResultView;
import com.ekusys.exam.exam.dto.TeacherExamView;
import com.ekusys.exam.repository.entity.AntiCheatEvent;
import com.ekusys.exam.repository.entity.Exam;
import com.ekusys.exam.repository.entity.ExamSession;
import com.ekusys.exam.repository.entity.ExamTargetClass;
import com.ekusys.exam.repository.entity.Paper;
import com.ekusys.exam.repository.entity.PaperQuestion;
import com.ekusys.exam.repository.entity.Question;
import com.ekusys.exam.repository.entity.StudentTeachingClass;
import com.ekusys.exam.repository.entity.Subject;
import com.ekusys.exam.repository.entity.Submission;
import com.ekusys.exam.repository.entity.SubmissionAnswer;
import com.ekusys.exam.repository.entity.TeachingClass;
import com.ekusys.exam.repository.entity.User;
import com.ekusys.exam.repository.mapper.AntiCheatEventMapper;
import com.ekusys.exam.repository.mapper.ExamMapper;
import com.ekusys.exam.repository.mapper.ExamSessionMapper;
import com.ekusys.exam.repository.mapper.ExamTargetClassMapper;
import com.ekusys.exam.repository.mapper.PaperMapper;
import com.ekusys.exam.repository.mapper.PaperQuestionMapper;
import com.ekusys.exam.repository.mapper.QuestionMapper;
import com.ekusys.exam.repository.mapper.StudentTeachingClassMapper;
import com.ekusys.exam.repository.mapper.SubjectMapper;
import com.ekusys.exam.repository.mapper.SubmissionAnswerMapper;
import com.ekusys.exam.repository.mapper.SubmissionMapper;
import com.ekusys.exam.repository.mapper.TeachingClassMapper;
import com.ekusys.exam.repository.mapper.UserMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExamService {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_TEACHER = "TEACHER";
    private static final String ENROLL_STATUS_ACTIVE = "ACTIVE";

    private final ExamMapper examMapper;
    private final PaperMapper paperMapper;
    private final ExamTargetClassMapper examTargetClassMapper;
    private final StudentTeachingClassMapper studentTeachingClassMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final SubjectMapper subjectMapper;
    private final UserMapper userMapper;
    private final ExamSessionMapper examSessionMapper;
    private final SubmissionMapper submissionMapper;
    private final SubmissionAnswerMapper submissionAnswerMapper;
    private final PaperQuestionMapper paperQuestionMapper;
    private final QuestionMapper questionMapper;
    private final AntiCheatEventMapper antiCheatEventMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ExamService(ExamMapper examMapper,
                       PaperMapper paperMapper,
                       ExamTargetClassMapper examTargetClassMapper,
                       StudentTeachingClassMapper studentTeachingClassMapper,
                       TeachingClassMapper teachingClassMapper,
                       SubjectMapper subjectMapper,
                       UserMapper userMapper,
                       ExamSessionMapper examSessionMapper,
                       SubmissionMapper submissionMapper,
                       SubmissionAnswerMapper submissionAnswerMapper,
                       PaperQuestionMapper paperQuestionMapper,
                       QuestionMapper questionMapper,
                       AntiCheatEventMapper antiCheatEventMapper,
                       StringRedisTemplate redisTemplate,
                       ObjectMapper objectMapper) {
        this.examMapper = examMapper;
        this.paperMapper = paperMapper;
        this.examTargetClassMapper = examTargetClassMapper;
        this.studentTeachingClassMapper = studentTeachingClassMapper;
        this.teachingClassMapper = teachingClassMapper;
        this.subjectMapper = subjectMapper;
        this.userMapper = userMapper;
        this.examSessionMapper = examSessionMapper;
        this.submissionMapper = submissionMapper;
        this.submissionAnswerMapper = submissionAnswerMapper;
        this.paperQuestionMapper = paperQuestionMapper;
        this.questionMapper = questionMapper;
        this.antiCheatEventMapper = antiCheatEventMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Long createExam(ExamCreateRequest request) {
        Paper paper = paperMapper.selectById(request.getPaperId());
        if (paper == null) {
            throw new BusinessException("试卷不存在");
        }
        Long paperSubjectId = paper.getSubjectId();
        if (paperSubjectId == null) {
            throw new BusinessException("试卷未绑定课程，无法发布考试");
        }

        Set<Long> targetClassIds = request.getTargetClassIds().stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (targetClassIds.isEmpty()) {
            throw new BusinessException("目标教学班不能为空");
        }

        List<TeachingClass> teachingClasses = teachingClassMapper.selectBatchIds(targetClassIds);
        if (teachingClasses.size() != targetClassIds.size()) {
            throw new BusinessException("存在无效教学班ID");
        }
        Map<Long, TeachingClass> teachingClassMap = teachingClasses.stream()
            .collect(Collectors.toMap(TeachingClass::getId, tc -> tc, (a, b) -> a));

        Long publisherId = SecurityUtils.getCurrentUserId();
        Set<String> publisherRoleCodes = new HashSet<>(userMapper.selectRoleCodes(publisherId));
        for (Long classId : targetClassIds) {
            TeachingClass teachingClass = teachingClassMap.get(classId);
            if (teachingClass == null) {
                throw new BusinessException("存在无效教学班ID");
            }
            if (!Objects.equals(paperSubjectId, teachingClass.getSubjectId())) {
                throw new BusinessException("目标教学班课程与试卷课程不一致");
            }
            validateTeachingClassTeacher(teachingClass);
            if (!publisherRoleCodes.contains(ROLE_ADMIN)
                && publisherRoleCodes.contains(ROLE_TEACHER)
                && !Objects.equals(publisherId, teachingClass.getTeacherId())) {
                throw new BusinessException("仅可为自己授课的教学班发布考试");
            }
        }

        Exam exam = new Exam();
        exam.setName(request.getName());
        exam.setPaperId(request.getPaperId());
        exam.setStartTime(request.getStartTime());
        exam.setEndTime(request.getEndTime());
        exam.setDurationMinutes(request.getDurationMinutes());
        exam.setPassScore(request.getPassScore());
        exam.setStatus(ExamStatus.DRAFT.name());
        exam.setPublisherId(SecurityUtils.getCurrentUserId());
        examMapper.insert(exam);

        for (Long classId : targetClassIds) {
            ExamTargetClass target = new ExamTargetClass();
            target.setExamId(exam.getId());
            target.setClassId(classId);
            examTargetClassMapper.insert(target);
        }
        return exam.getId();
    }

    @Transactional
    public void publishExam(Long examId) {
        Exam exam = ensureExam(examId);
        exam.setStatus(ExamStatus.PUBLISHED.name());
        examMapper.updateById(exam);
    }

    public List<StudentExamView> listStudentExams() {
        Long studentId = SecurityUtils.getCurrentUserId();
        List<Long> classIds = listActiveTeachingClassIdsByStudent(studentId);
        if (classIds.isEmpty()) {
            return List.of();
        }

        List<ExamTargetClass> targets = examTargetClassMapper.selectList(
            new LambdaQueryWrapper<ExamTargetClass>().in(ExamTargetClass::getClassId, classIds)
        );
        Set<Long> examIds = targets.stream().map(ExamTargetClass::getExamId).collect(Collectors.toSet());
        if (examIds.isEmpty()) {
            return List.of();
        }

        List<Submission> submissions = submissionMapper.selectList(
            new LambdaQueryWrapper<Submission>().eq(Submission::getStudentId, studentId).in(Submission::getExamId, examIds)
        );
        Set<Long> submittedIds = submissions.stream()
            .filter(s -> SubmissionStatus.SUBMITTED.name().equals(s.getStatus()) || SubmissionStatus.GRADED.name().equals(s.getStatus()))
            .map(Submission::getExamId)
            .collect(Collectors.toSet());

        return examMapper.selectBatchIds(examIds).stream()
            .sorted((a, b) -> b.getStartTime().compareTo(a.getStartTime()))
            .map(exam -> StudentExamView.builder()
                .examId(exam.getId())
                .name(exam.getName())
                .startTime(exam.getStartTime())
                .endTime(exam.getEndTime())
                .durationMinutes(exam.getDurationMinutes())
                .status(exam.getStatus())
                .submitted(submittedIds.contains(exam.getId()))
                .build())
            .toList();
    }

    public List<TeacherExamView> listTeacherExams() {
        List<Exam> exams = examMapper.selectList(new LambdaQueryWrapper<Exam>().orderByDesc(Exam::getCreateTime));
        return exams.stream().map(exam -> TeacherExamView.builder()
            .examId(exam.getId())
            .name(exam.getName())
            .startTime(exam.getStartTime())
            .endTime(exam.getEndTime())
            .durationMinutes(exam.getDurationMinutes())
            .passScore(exam.getPassScore())
            .status(exam.getStatus())
            .build()).toList();
    }

    public List<TeachingClassOptionView> listTeachingClasses() {
        Long userId = SecurityUtils.getCurrentUserId();
        Set<String> roleCodes = new HashSet<>(userMapper.selectRoleCodes(userId));

        LambdaQueryWrapper<TeachingClass> wrapper = new LambdaQueryWrapper<TeachingClass>()
            .orderByAsc(TeachingClass::getSubjectId, TeachingClass::getTerm, TeachingClass::getName, TeachingClass::getId);
        if (!roleCodes.contains(ROLE_ADMIN)) {
            wrapper.eq(TeachingClass::getTeacherId, userId);
        }
        List<TeachingClass> classes = teachingClassMapper.selectList(wrapper);
        if (classes.isEmpty()) {
            return List.of();
        }

        Set<Long> subjectIds = classes.stream()
            .map(TeachingClass::getSubjectId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, Subject> subjectMap = subjectIds.isEmpty()
            ? Map.of()
            : subjectMapper.selectBatchIds(subjectIds).stream()
                .collect(Collectors.toMap(Subject::getId, s -> s, (a, b) -> a));

        Set<Long> teacherIds = classes.stream()
            .map(TeachingClass::getTeacherId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, User> teacherMap = teacherIds.isEmpty()
            ? Map.of()
            : userMapper.selectBatchIds(teacherIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        return classes.stream().map(tc -> {
            Subject subject = subjectMap.get(tc.getSubjectId());
            User teacher = teacherMap.get(tc.getTeacherId());
            return TeachingClassOptionView.builder()
                .id(tc.getId())
                .name(tc.getName())
                .subjectId(tc.getSubjectId())
                .subjectName(subject == null ? null : subject.getName())
                .teacherId(tc.getTeacherId())
                .teacherName(teacher == null ? null : teacher.getRealName())
                .term(tc.getTerm())
                .status(tc.getStatus())
                .build();
        }).toList();
    }

    @Transactional
    public StartExamResponse startExam(Long examId) {
        Long studentId = SecurityUtils.getCurrentUserId();
        Exam exam = ensureExam(examId);
        checkStudentAccess(examId, studentId);

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(exam.getStartTime())) {
            throw new BusinessException("考试尚未开始");
        }
        if (now.isAfter(exam.getEndTime())) {
            throw new BusinessException("考试已结束");
        }
        if (ExamStatus.DRAFT.name().equals(exam.getStatus())) {
            throw new BusinessException("考试未发布");
        }
        if (ExamStatus.PUBLISHED.name().equals(exam.getStatus())) {
            exam.setStatus(ExamStatus.ONGOING.name());
            examMapper.updateById(exam);
        }

        ExamSession session = examSessionMapper.selectOne(new LambdaQueryWrapper<ExamSession>()
            .eq(ExamSession::getExamId, examId)
            .eq(ExamSession::getStudentId, studentId)
            .last("limit 1"));
        if (session != null && SessionStatus.SUBMITTED.name().equals(session.getStatus())) {
            throw new BusinessException("你已提交过本场考试");
        }
        if (session == null) {
            session = new ExamSession();
            session.setExamId(examId);
            session.setStudentId(studentId);
            session.setStatus(SessionStatus.ANSWERING.name());
            session.setStartTime(now);
            examSessionMapper.insert(session);
        } else {
            session.setStatus(SessionStatus.ANSWERING.name());
            examSessionMapper.updateById(session);
        }

        Submission submission = submissionMapper.selectOne(new LambdaQueryWrapper<Submission>()
            .eq(Submission::getExamId, examId)
            .eq(Submission::getStudentId, studentId)
            .last("limit 1"));
        if (submission == null) {
            submission = new Submission();
            submission.setExamId(examId);
            submission.setStudentId(studentId);
            submission.setStatus(SubmissionStatus.IN_PROGRESS.name());
            submission.setObjectiveScore(0);
            submission.setSubjectiveScore(0);
            submission.setTotalScore(0);
            submission.setPassFlag(false);
            submissionMapper.insert(submission);
        }

        List<PaperQuestion> paperQuestions = paperQuestionMapper.selectList(
            new LambdaQueryWrapper<PaperQuestion>().eq(PaperQuestion::getPaperId, exam.getPaperId()).orderByAsc(PaperQuestion::getSortOrder)
        );
        List<Long> qIds = paperQuestions.stream().map(PaperQuestion::getQuestionId).toList();
        Map<Long, Question> questionMap = questionMapper.selectBatchIds(qIds).stream()
            .collect(Collectors.toMap(Question::getId, q -> q));

        Map<Long, String> snapshotAnswers = loadSnapshotAnswerMap(examId, studentId);
        List<StudentExamQuestionView> questions = paperQuestions.stream().map(link -> {
            Question q = questionMap.get(link.getQuestionId());
            return StudentExamQuestionView.builder()
                .questionId(link.getQuestionId())
                .type(q == null ? null : q.getType())
                .content(q == null ? null : q.getContent())
                .optionsJson(q == null ? null : q.getOptionsJson())
                .score(link.getScore())
                .sortOrder(link.getSortOrder())
                .currentAnswer(snapshotAnswers.get(link.getQuestionId()))
                .build();
        }).toList();

        return StartExamResponse.builder()
            .examId(exam.getId())
            .examName(exam.getName())
            .durationMinutes(exam.getDurationMinutes())
            .startTime(exam.getStartTime())
            .endTime(exam.getEndTime())
            .questions(questions)
            .build();
    }

    public void saveSnapshot(Long examId, SnapshotRequest request) {
        Long studentId = SecurityUtils.getCurrentUserId();
        checkStudentAccess(examId, studentId);

        ExamSession session = examSessionMapper.selectOne(new LambdaQueryWrapper<ExamSession>()
            .eq(ExamSession::getExamId, examId)
            .eq(ExamSession::getStudentId, studentId)
            .last("limit 1"));
        if (session == null || SessionStatus.SUBMITTED.name().equals(session.getStatus())) {
            throw new BusinessException("会话已结束");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("examId", examId);
        payload.put("studentId", studentId);
        payload.put("answers", request.getAnswers());
        payload.put("timestamp", request.getClientTimestamp() == null ? System.currentTimeMillis() : request.getClientTimestamp());

        try {
            redisTemplate.opsForValue().set(snapshotKey(examId, studentId), objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new BusinessException("快照序列化失败");
        }

        session.setLastSnapshotTime(LocalDateTime.now());
        examSessionMapper.updateById(session);
    }

    @Transactional
    public void recordAntiCheatEvent(Long examId, AntiCheatEventRequest request) {
        Long studentId = SecurityUtils.getCurrentUserId();
        checkStudentAccess(examId, studentId);
        AntiCheatEvent event = new AntiCheatEvent();
        event.setExamId(examId);
        event.setStudentId(studentId);
        event.setEventType(request.getEventType());
        event.setDurationMs(request.getDurationMs());
        event.setPayload(request.getPayload());
        event.setEventTime(LocalDateTime.now());
        antiCheatEventMapper.insert(event);
    }

    @Transactional
    public SubmitResultView submit(Long examId, SubmitExamRequest request) {
        Long studentId = SecurityUtils.getCurrentUserId();
        Exam exam = ensureExam(examId);
        checkStudentAccess(examId, studentId);

        ExamSession session = examSessionMapper.selectOne(new LambdaQueryWrapper<ExamSession>()
            .eq(ExamSession::getExamId, examId)
            .eq(ExamSession::getStudentId, studentId)
            .last("limit 1"));
        if (session == null || SessionStatus.SUBMITTED.name().equals(session.getStatus())) {
            throw new BusinessException("不可重复交卷");
        }

        Submission submission = submissionMapper.selectOne(new LambdaQueryWrapper<Submission>()
            .eq(Submission::getExamId, examId)
            .eq(Submission::getStudentId, studentId)
            .last("limit 1"));
        if (submission == null) {
            submission = new Submission();
            submission.setExamId(examId);
            submission.setStudentId(studentId);
            submission.setStatus(SubmissionStatus.IN_PROGRESS.name());
            submissionMapper.insert(submission);
        }

        Map<Long, String> answerMap = request.getAnswers().stream()
            .collect(Collectors.toMap(AnswerPayload::getQuestionId, AnswerPayload::getAnswerText, (a, b) -> b));

        List<PaperQuestion> paperQuestions = paperQuestionMapper.selectList(
            new LambdaQueryWrapper<PaperQuestion>().eq(PaperQuestion::getPaperId, exam.getPaperId()).orderByAsc(PaperQuestion::getSortOrder)
        );
        List<Long> qIds = paperQuestions.stream().map(PaperQuestion::getQuestionId).toList();
        Map<Long, Question> questionMap = questionMapper.selectBatchIds(qIds).stream()
            .collect(Collectors.toMap(Question::getId, q -> q));

        submissionAnswerMapper.delete(new LambdaQueryWrapper<SubmissionAnswer>().eq(SubmissionAnswer::getSubmissionId, submission.getId()));

        int objectiveScore = 0;
        int subjectiveScore = 0;
        boolean hasShort = false;

        for (PaperQuestion link : paperQuestions) {
            Question q = questionMap.get(link.getQuestionId());
            String userAnswer = answerMap.getOrDefault(link.getQuestionId(), "");

            SubmissionAnswer item = new SubmissionAnswer();
            item.setSubmissionId(submission.getId());
            item.setQuestionId(link.getQuestionId());
            item.setAnswerText(userAnswer);
            item.setFinalAnswer(true);
            item.setSource("SUBMIT");

            if (q != null && AnswerJudgeUtil.isObjectiveType(q.getType())) {
                boolean correct = AnswerJudgeUtil.isCorrect(q.getType(), q.getAnswer(), userAnswer);
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

        if (hasShort) {
            submission.setStatus(SubmissionStatus.SUBMITTED.name());
            submission.setPassFlag(submission.getTotalScore() >= exam.getPassScore());
        } else {
            submission.setStatus(SubmissionStatus.GRADED.name());
            submission.setPassFlag(submission.getTotalScore() >= exam.getPassScore());
        }
        submissionMapper.updateById(submission);

        session.setStatus(SessionStatus.SUBMITTED.name());
        session.setEndTime(LocalDateTime.now());
        examSessionMapper.updateById(session);

        redisTemplate.delete(snapshotKey(examId, studentId));

        return SubmitResultView.builder()
            .submissionId(submission.getId())
            .objectiveScore(submission.getObjectiveScore())
            .subjectiveScore(submission.getSubjectiveScore())
            .totalScore(submission.getTotalScore())
            .passFlag(submission.getPassFlag())
            .status(submission.getStatus())
            .build();
    }

    public Map<Long, String> loadSnapshotAnswerMap(Long examId, Long studentId) {
        String key = snapshotKey(examId, studentId);
        String value = redisTemplate.opsForValue().get(key);
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(value, Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> answers = (List<Map<String, Object>>) map.getOrDefault("answers", List.of());
            Map<Long, String> result = new HashMap<>();
            for (Map<String, Object> answer : answers) {
                Long qid = Long.valueOf(answer.get("questionId").toString());
                String text = String.valueOf(answer.get("answerText"));
                result.put(qid, text);
            }
            return result;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    public void flushAllSnapshotsToDatabase() {
        Set<String> keys = redisTemplate.keys("exam:snapshot:*");
        if (keys == null || keys.isEmpty()) {
            return;
        }
        for (String key : keys) {
            flushSnapshotKey(key);
        }
    }

    @Transactional
    public void flushSnapshotKey(String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (value == null || value.isBlank()) {
            return;
        }
        String[] parts = key.split(":");
        if (parts.length != 4) {
            return;
        }
        Long examId = Long.valueOf(parts[2]);
        Long studentId = Long.valueOf(parts[3]);

        Submission submission = submissionMapper.selectOne(new LambdaQueryWrapper<Submission>()
            .eq(Submission::getExamId, examId)
            .eq(Submission::getStudentId, studentId)
            .eq(Submission::getStatus, SubmissionStatus.IN_PROGRESS.name())
            .last("limit 1"));
        if (submission == null) {
            return;
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(value, Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> answers = (List<Map<String, Object>>) payload.getOrDefault("answers", new ArrayList<>());

            submissionAnswerMapper.delete(new LambdaQueryWrapper<SubmissionAnswer>()
                .eq(SubmissionAnswer::getSubmissionId, submission.getId())
                .eq(SubmissionAnswer::getFinalAnswer, false));

            for (Map<String, Object> answer : answers) {
                SubmissionAnswer item = new SubmissionAnswer();
                item.setSubmissionId(submission.getId());
                item.setQuestionId(Long.valueOf(answer.get("questionId").toString()));
                item.setAnswerText(String.valueOf(answer.get("answerText")));
                item.setFinalAnswer(false);
                item.setSource("SNAPSHOT");
                item.setObjectiveCorrect(null);
                item.setObjectiveScore(0);
                submissionAnswerMapper.insert(item);
            }
            redisTemplate.delete(key);
        } catch (Exception ignored) {
        }
    }

    private Exam ensureExam(Long examId) {
        Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }
        return exam;
    }

    private void checkStudentAccess(Long examId, Long studentId) {
        List<Long> classIds = listActiveTeachingClassIdsByStudent(studentId);
        if (classIds.isEmpty()) {
            throw new BusinessException("未关联教学班");
        }
        long count = examTargetClassMapper.selectCount(new LambdaQueryWrapper<ExamTargetClass>()
            .eq(ExamTargetClass::getExamId, examId)
            .in(ExamTargetClass::getClassId, classIds));
        if (count == 0) {
            throw new BusinessException("无考试访问权限");
        }
    }

    private List<Long> listActiveTeachingClassIdsByStudent(Long studentId) {
        return studentTeachingClassMapper.selectList(
            new LambdaQueryWrapper<StudentTeachingClass>()
                .eq(StudentTeachingClass::getStudentId, studentId)
                .eq(StudentTeachingClass::getEnrollStatus, ENROLL_STATUS_ACTIVE)
        ).stream().map(StudentTeachingClass::getTeachingClassId).toList();
    }

    private void validateTeachingClassTeacher(TeachingClass teachingClass) {
        Long teacherId = teachingClass.getTeacherId();
        if (teacherId == null) {
            throw new BusinessException("教学班未配置任课老师");
        }
        User teacher = userMapper.selectById(teacherId);
        if (teacher == null) {
            throw new BusinessException("教学班任课老师不存在");
        }
        Set<String> roleCodes = new HashSet<>(userMapper.selectRoleCodes(teacherId));
        if (!roleCodes.contains(ROLE_TEACHER)) {
            throw new BusinessException("教学班任课老师角色非法");
        }
    }

    private String snapshotKey(Long examId, Long studentId) {
        return "exam:snapshot:" + examId + ":" + studentId;
    }
}
