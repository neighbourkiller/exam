package com.ekusys.exam.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ekusys.exam.common.config.AppSnapshotProperties;
import com.ekusys.exam.common.enums.SubmissionStatus;
import com.ekusys.exam.common.exception.BusinessException;
import com.ekusys.exam.exam.dto.SnapshotRequest;
import com.ekusys.exam.repository.entity.Exam;
import com.ekusys.exam.repository.entity.ExamSession;
import com.ekusys.exam.repository.entity.Submission;
import com.ekusys.exam.repository.entity.SubmissionAnswer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import com.ekusys.exam.repository.mapper.SubmissionAnswerMapper;
import com.ekusys.exam.repository.mapper.SubmissionMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExamSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(ExamSnapshotService.class);

    private final ExamAccessService examAccessService;
    private final ExamSessionService examSessionService;
    private final AppSnapshotProperties snapshotProperties;
    private final SubmissionMapper submissionMapper;
    private final SubmissionAnswerMapper submissionAnswerMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ExamSnapshotService(ExamAccessService examAccessService,
                               ExamSessionService examSessionService,
                               AppSnapshotProperties snapshotProperties,
                               SubmissionMapper submissionMapper,
                               SubmissionAnswerMapper submissionAnswerMapper,
                               StringRedisTemplate redisTemplate,
                               ObjectMapper objectMapper) {
        this.examAccessService = examAccessService;
        this.examSessionService = examSessionService;
        this.snapshotProperties = snapshotProperties;
        this.submissionMapper = submissionMapper;
        this.submissionAnswerMapper = submissionAnswerMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void saveSnapshot(Long examId, SnapshotRequest request) {
        Long studentId = examAccessService.getCurrentUserId();
        Exam exam = examAccessService.ensureExam(examId);
        examAccessService.checkStudentAccess(examId, studentId);

        ExamSession session = examSessionService.requireActiveSession(examId, studentId);
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> payload = new HashMap<>();
        payload.put("examId", examId);
        payload.put("studentId", studentId);
        payload.put("answers", request.getAnswers());
        payload.put("timestamp", request.getClientTimestamp() == null ? System.currentTimeMillis() : request.getClientTimestamp());

        try {
            redisTemplate.opsForValue().set(
                snapshotKey(examId, studentId),
                objectMapper.writeValueAsString(payload),
                resolveSnapshotTtl(exam, now)
            );
        } catch (JsonProcessingException e) {
            throw new BusinessException("快照序列化失败");
        }

        examSessionService.touchSnapshot(session, now);
    }

    public Map<Long, String> loadSnapshotAnswerMap(Long examId, Long studentId) {
        Map<Long, String> persisted = loadPersistedDraftAnswerMap(examId, studentId);
        Map<Long, String> snapshot = loadRedisSnapshotAnswerMap(examId, studentId);
        if (persisted.isEmpty()) {
            return snapshot;
        }
        if (snapshot.isEmpty()) {
            return persisted;
        }
        Map<Long, String> merged = new LinkedHashMap<>(persisted);
        merged.putAll(snapshot);
        return merged;
    }

    private Map<Long, String> loadRedisSnapshotAnswerMap(Long examId, Long studentId) {
        String value = redisTemplate.opsForValue().get(snapshotKey(examId, studentId));
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
        } catch (Exception ex) {
            log.warn("Failed to parse snapshot payload: examId={}, studentId={}", examId, studentId, ex);
            return Map.of();
        }
    }

    public Map<Long, String> loadPersistedDraftAnswerMap(Long examId, Long studentId) {
        Submission submission = submissionMapper.selectOne(new LambdaQueryWrapper<Submission>()
            .eq(Submission::getExamId, examId)
            .eq(Submission::getStudentId, studentId)
            .eq(Submission::getStatus, SubmissionStatus.IN_PROGRESS.name())
            .last("limit 1"));
        if (submission == null) {
            return Map.of();
        }
        return submissionAnswerMapper.selectList(new LambdaQueryWrapper<SubmissionAnswer>()
            .eq(SubmissionAnswer::getSubmissionId, submission.getId())
            .eq(SubmissionAnswer::getFinalAnswer, false)
            .orderByAsc(SubmissionAnswer::getId))
            .stream()
            .filter(item -> item.getQuestionId() != null)
            .collect(java.util.stream.Collectors.toMap(
                SubmissionAnswer::getQuestionId,
                item -> item.getAnswerText() == null ? "" : item.getAnswerText(),
                (a, b) -> b,
                LinkedHashMap::new
            ));
    }

    public void clearSnapshot(Long examId, Long studentId) {
        redisTemplate.delete(snapshotKey(examId, studentId));
    }

    public void flushAllSnapshotsToDatabase() {
        Set<String> keys = findSnapshotKeys();
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
            .last("limit 1"));
        if (submission == null) {
            return;
        }
        if (!SubmissionStatus.IN_PROGRESS.name().equals(submission.getStatus())) {
            log.debug("Skip snapshot flush because submission is no longer in progress: examId={}, studentId={}, status={}",
                examId, studentId, submission.getStatus());
            redisTemplate.delete(key);
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
        } catch (Exception ex) {
            log.error("Failed to flush snapshot to database: key={}, examId={}, studentId={}", key, examId, studentId, ex);
        }
    }

    private Set<String> findSnapshotKeys() {
        StringRedisSerializer serializer = new StringRedisSerializer(StandardCharsets.UTF_8);
        return redisTemplate.execute((RedisCallback<Set<String>>) connection -> {
            Set<String> keys = new java.util.LinkedHashSet<>();
            ScanOptions options = ScanOptions.scanOptions()
                .match("exam:snapshot:*")
                .count(200)
                .build();
            try (var cursor = connection.scan(options)) {
                while (cursor.hasNext()) {
                    String key = serializer.deserialize(cursor.next());
                    if (key != null && !key.isBlank()) {
                        keys.add(key);
                    }
                }
            } catch (Exception ex) {
                log.error("Failed to scan snapshot keys", ex);
            }
            return keys;
        });
    }

    private Duration resolveSnapshotTtl(Exam exam, LocalDateTime now) {
        Duration fallback = Duration.ofHours(Math.max(1L, snapshotProperties.getTtlHours()));
        if (exam == null || exam.getEndTime() == null || now == null) {
            return fallback;
        }
        if (!exam.getEndTime().isAfter(now)) {
            return fallback;
        }
        return Duration.between(now, exam.getEndTime()).plus(fallback);
    }

    private String snapshotKey(Long examId, Long studentId) {
        return "exam:snapshot:" + examId + ":" + studentId;
    }
}
