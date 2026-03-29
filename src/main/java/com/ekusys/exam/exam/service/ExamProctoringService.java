package com.ekusys.exam.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ekusys.exam.common.enums.SessionStatus;
import com.ekusys.exam.common.exception.BusinessException;
import com.ekusys.exam.common.security.SecurityUtils;
import com.ekusys.exam.exam.dto.ProctoringEventStatView;
import com.ekusys.exam.exam.dto.ProctoringOverviewView;
import com.ekusys.exam.exam.dto.ProctoringRecentEventView;
import com.ekusys.exam.exam.dto.ProctoringStudentTimelineView;
import com.ekusys.exam.exam.dto.ProctoringStudentView;
import com.ekusys.exam.exam.dto.ProctoringTimelineEventView;
import com.ekusys.exam.repository.entity.AntiCheatEvent;
import com.ekusys.exam.repository.entity.Exam;
import com.ekusys.exam.repository.entity.ExamSession;
import com.ekusys.exam.repository.entity.ExamTargetClass;
import com.ekusys.exam.repository.entity.StudentTeachingClass;
import com.ekusys.exam.repository.entity.Submission;
import com.ekusys.exam.repository.entity.TeachingClass;
import com.ekusys.exam.repository.entity.User;
import com.ekusys.exam.repository.mapper.AntiCheatEventMapper;
import com.ekusys.exam.repository.mapper.ExamMapper;
import com.ekusys.exam.repository.mapper.ExamSessionMapper;
import com.ekusys.exam.repository.mapper.ExamTargetClassMapper;
import com.ekusys.exam.repository.mapper.StudentTeachingClassMapper;
import com.ekusys.exam.repository.mapper.SubmissionMapper;
import com.ekusys.exam.repository.mapper.TeachingClassMapper;
import com.ekusys.exam.repository.mapper.UserMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ExamProctoringService {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_TEACHER = "TEACHER";
    private static final String ENROLL_STATUS_ACTIVE = "ACTIVE";

    private static final String EVENT_WINDOW_BLUR = "WINDOW_BLUR";
    private static final String EVENT_TAB_HIDDEN = "TAB_HIDDEN";
    private static final String EVENT_FULLSCREEN_EXIT = "FULLSCREEN_EXIT";
    private static final String EVENT_COPY_ATTEMPT = "COPY_ATTEMPT";
    private static final String EVENT_PASTE_ATTEMPT = "PASTE_ATTEMPT";
    private static final String EVENT_CUT_ATTEMPT = "CUT_ATTEMPT";
    private static final String EVENT_CONTEXT_MENU = "CONTEXT_MENU";
    private static final String EVENT_NETWORK_OFFLINE = "NETWORK_OFFLINE";

    private final ExamMapper examMapper;
    private final ExamTargetClassMapper examTargetClassMapper;
    private final StudentTeachingClassMapper studentTeachingClassMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final UserMapper userMapper;
    private final ExamSessionMapper examSessionMapper;
    private final SubmissionMapper submissionMapper;
    private final AntiCheatEventMapper antiCheatEventMapper;

    public ExamProctoringService(ExamMapper examMapper,
                                 ExamTargetClassMapper examTargetClassMapper,
                                 StudentTeachingClassMapper studentTeachingClassMapper,
                                 TeachingClassMapper teachingClassMapper,
                                 UserMapper userMapper,
                                 ExamSessionMapper examSessionMapper,
                                 SubmissionMapper submissionMapper,
                                 AntiCheatEventMapper antiCheatEventMapper) {
        this.examMapper = examMapper;
        this.examTargetClassMapper = examTargetClassMapper;
        this.studentTeachingClassMapper = studentTeachingClassMapper;
        this.teachingClassMapper = teachingClassMapper;
        this.userMapper = userMapper;
        this.examSessionMapper = examSessionMapper;
        this.submissionMapper = submissionMapper;
        this.antiCheatEventMapper = antiCheatEventMapper;
    }

    public ProctoringOverviewView getOverview(Long examId) {
        ProctoringContext context = buildContext(examId);
        Collection<StudentRiskSnapshot> students = context.studentSnapshots.values();

        int lowRiskCount = 0;
        int mediumRiskCount = 0;
        int highRiskCount = 0;
        int snapshotAlertCount = 0;
        int answeringStudents = 0;
        for (StudentRiskSnapshot snapshot : students) {
            if (snapshot.answering()) {
                answeringStudents++;
            }
            if (snapshot.snapshotAlert()) {
                snapshotAlertCount++;
            }
            switch (snapshot.riskLevel()) {
                case "HIGH" -> highRiskCount++;
                case "MEDIUM" -> mediumRiskCount++;
                default -> lowRiskCount++;
            }
        }

        return ProctoringOverviewView.builder()
            .examId(context.exam.getId())
            .examName(context.exam.getName())
            .examStatus(context.exam.getStatus())
            .totalStudents(students.size())
            .answeringStudents(answeringStudents)
            .lowRiskCount(lowRiskCount)
            .mediumRiskCount(mediumRiskCount)
            .highRiskCount(highRiskCount)
            .snapshotAlertCount(snapshotAlertCount)
            .recentEvents(buildRecentEvents(context))
            .eventTypeStats(buildEventTypeStats(context.eventsByStudent.values().stream().flatMap(List::stream).toList()))
            .build();
    }

    public List<ProctoringStudentView> listStudents(Long examId) {
        ProctoringContext context = buildContext(examId);
        return context.studentSnapshots.values().stream()
            .map(this::toStudentView)
            .sorted(Comparator
                .comparing(ProctoringStudentView::getRiskScore, Comparator.reverseOrder())
                .thenComparing(ProctoringStudentView::getLastEventTime, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ProctoringStudentView::getStudentId))
            .toList();
    }

    public ProctoringStudentTimelineView getStudentTimeline(Long examId, Long studentId) {
        ProctoringContext context = buildContext(examId);
        StudentRiskSnapshot snapshot = context.studentSnapshots.get(studentId);
        if (snapshot == null) {
            throw new BusinessException("学生不在该考试监考范围内");
        }
        List<AntiCheatEvent> events = context.eventsByStudent.getOrDefault(studentId, List.of());
        return ProctoringStudentTimelineView.builder()
            .examId(context.exam.getId())
            .examName(context.exam.getName())
            .examStatus(context.exam.getStatus())
            .studentId(snapshot.studentId())
            .studentName(snapshot.studentName())
            .username(snapshot.username())
            .classNames(snapshot.classNames())
            .riskScore(snapshot.riskScore())
            .riskLevel(snapshot.riskLevel())
            .eventCount(snapshot.eventCount())
            .latestEventType(snapshot.latestEventType())
            .lastEventTime(snapshot.lastEventTime())
            .lastSnapshotTime(snapshot.lastSnapshotTime())
            .answering(snapshot.answering())
            .snapshotAlert(snapshot.snapshotAlert())
            .totalOffscreenDurationMs(snapshot.totalOffscreenDurationMs())
            .longOffscreen(snapshot.longOffscreen())
            .eventTypeStats(buildEventTypeStats(events))
            .events(events.stream()
                .sorted(Comparator.comparing(AntiCheatEvent::getEventTime).reversed())
                .map(event -> ProctoringTimelineEventView.builder()
                    .eventType(event.getEventType())
                    .eventTime(event.getEventTime())
                    .durationMs(event.getDurationMs())
                    .payload(event.getPayload())
                    .build())
                .toList())
            .build();
    }

    private ProctoringContext buildContext(Long examId) {
        Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }
        List<ExamTargetClass> targetClasses = examTargetClassMapper.selectList(
            new LambdaQueryWrapper<ExamTargetClass>().eq(ExamTargetClass::getExamId, examId)
        );
        List<Long> classIds = targetClasses.stream().map(ExamTargetClass::getClassId).distinct().toList();
        Map<Long, TeachingClass> classMap = classIds.isEmpty()
            ? Map.of()
            : teachingClassMapper.selectBatchIds(classIds).stream()
                .collect(Collectors.toMap(TeachingClass::getId, item -> item, (a, b) -> a));
        ensureExamManagePermission(exam, classMap.values());

        List<StudentTeachingClass> enrollments = classIds.isEmpty()
            ? List.of()
            : studentTeachingClassMapper.selectList(
                new LambdaQueryWrapper<StudentTeachingClass>()
                    .in(StudentTeachingClass::getTeachingClassId, classIds)
                    .eq(StudentTeachingClass::getEnrollStatus, ENROLL_STATUS_ACTIVE)
            );
        Set<Long> studentIds = enrollments.stream()
            .map(StudentTeachingClass::getStudentId)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, User> userMap = studentIds.isEmpty()
            ? Map.of()
            : userMapper.selectBatchIds(studentIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user, (a, b) -> a));

        Map<Long, List<String>> classNamesByStudent = new LinkedHashMap<>();
        for (StudentTeachingClass enrollment : enrollments) {
            if (enrollment.getStudentId() == null) {
                continue;
            }
            TeachingClass teachingClass = classMap.get(enrollment.getTeachingClassId());
            classNamesByStudent.computeIfAbsent(enrollment.getStudentId(), key -> new ArrayList<>());
            if (teachingClass != null && teachingClass.getName() != null
                && !classNamesByStudent.get(enrollment.getStudentId()).contains(teachingClass.getName())) {
                classNamesByStudent.get(enrollment.getStudentId()).add(teachingClass.getName());
            }
        }

        Map<Long, ExamSession> sessionMap = studentIds.isEmpty()
            ? Map.of()
            : examSessionMapper.selectList(
                new LambdaQueryWrapper<ExamSession>()
                    .eq(ExamSession::getExamId, examId)
                    .in(ExamSession::getStudentId, studentIds)
            ).stream().collect(Collectors.toMap(
                ExamSession::getStudentId,
                session -> session,
                this::pickLatestSession
            ));

        Map<Long, Submission> submissionMap = studentIds.isEmpty()
            ? Map.of()
            : submissionMapper.selectList(
                new LambdaQueryWrapper<Submission>()
                    .eq(Submission::getExamId, examId)
                    .in(Submission::getStudentId, studentIds)
            ).stream().collect(Collectors.toMap(
                Submission::getStudentId,
                submission -> submission,
                this::pickLatestSubmission
            ));

        Map<Long, List<AntiCheatEvent>> eventsByStudent = studentIds.isEmpty()
            ? Map.of()
            : antiCheatEventMapper.selectList(
                new LambdaQueryWrapper<AntiCheatEvent>()
                    .eq(AntiCheatEvent::getExamId, examId)
                    .in(AntiCheatEvent::getStudentId, studentIds)
                    .orderByAsc(AntiCheatEvent::getEventTime, AntiCheatEvent::getId)
            ).stream().collect(Collectors.groupingBy(
                AntiCheatEvent::getStudentId,
                LinkedHashMap::new,
                Collectors.toList()
            ));

        LocalDateTime now = LocalDateTime.now();
        Map<Long, StudentRiskSnapshot> studentSnapshots = new LinkedHashMap<>();
        for (Long studentId : studentIds) {
            User user = userMap.get(studentId);
            ExamSession session = sessionMap.get(studentId);
            Submission submission = submissionMap.get(studentId);
            List<AntiCheatEvent> events = eventsByStudent.getOrDefault(studentId, List.of());
            studentSnapshots.put(studentId, buildStudentSnapshot(exam, studentId, user, classNamesByStudent.getOrDefault(studentId, List.of()), session, submission, events, now));
        }

        return new ProctoringContext(exam, studentSnapshots, eventsByStudent);
    }

    private StudentRiskSnapshot buildStudentSnapshot(Exam exam,
                                                     Long studentId,
                                                     User user,
                                                     List<String> classNames,
                                                     ExamSession session,
                                                     Submission submission,
                                                     List<AntiCheatEvent> events,
                                                     LocalDateTime now) {
        int riskScore = 0;
        Map<String, Deque<LocalDateTime>> recentEventsByType = new HashMap<>();
        Set<String> repeatBonusTypes = new HashSet<>();
        long totalOffscreenDurationMs = 0L;
        boolean longOffscreen = false;
        LocalDateTime lastEventTime = null;
        String latestEventType = null;

        for (AntiCheatEvent event : events) {
            riskScore += riskScoreOfEvent(event);
            if (EVENT_WINDOW_BLUR.equals(event.getEventType()) || EVENT_TAB_HIDDEN.equals(event.getEventType())) {
                long duration = safeDuration(event.getDurationMs());
                totalOffscreenDurationMs += duration;
                if (duration > 30_000L) {
                    longOffscreen = true;
                }
            }
            if (lastEventTime == null || (event.getEventTime() != null && event.getEventTime().isAfter(lastEventTime))) {
                lastEventTime = event.getEventTime();
                latestEventType = event.getEventType();
            }

            if (event.getEventTime() == null || event.getEventType() == null) {
                continue;
            }
            Deque<LocalDateTime> recent = recentEventsByType.computeIfAbsent(event.getEventType(), key -> new ArrayDeque<>());
            while (!recent.isEmpty() && Duration.between(recent.peekFirst(), event.getEventTime()).toMinutes() >= 10) {
                recent.pollFirst();
            }
            recent.addLast(event.getEventTime());
            if (recent.size() >= 3 && repeatBonusTypes.add(event.getEventType())) {
                riskScore += 2;
            }
        }

        if (!longOffscreen && totalOffscreenDurationMs > 30_000L) {
            longOffscreen = true;
        }

        SnapshotGap snapshotGap = computeSnapshotGap(exam, session, submission, now);
        riskScore += snapshotGap.riskPoints();

        return new StudentRiskSnapshot(
            studentId,
            user == null ? "学生" + studentId : coalesce(user.getRealName(), user.getUsername(), "学生" + studentId),
            user == null ? null : user.getUsername(),
            List.copyOf(classNames),
            riskScore,
            riskLevelOf(riskScore),
            events.size(),
            latestEventType,
            lastEventTime,
            session == null ? null : session.getLastSnapshotTime(),
            session != null && SessionStatus.ANSWERING.name().equals(session.getStatus()),
            snapshotGap.alert(),
            totalOffscreenDurationMs,
            longOffscreen
        );
    }

    private int riskScoreOfEvent(AntiCheatEvent event) {
        if (event == null || event.getEventType() == null) {
            return 0;
        }
        long durationMs = safeDuration(event.getDurationMs());
        return switch (event.getEventType()) {
            case EVENT_WINDOW_BLUR, EVENT_TAB_HIDDEN -> {
                int score = 2;
                if (durationMs > 5_000L) {
                    score += 2;
                }
                if (durationMs > 30_000L) {
                    score += 3;
                }
                yield score;
            }
            case EVENT_FULLSCREEN_EXIT -> 3;
            case EVENT_COPY_ATTEMPT, EVENT_PASTE_ATTEMPT, EVENT_CUT_ATTEMPT, EVENT_CONTEXT_MENU -> 2;
            case EVENT_NETWORK_OFFLINE -> durationMs > 10_000L ? 2 : 0;
            default -> 0;
        };
    }

    private SnapshotGap computeSnapshotGap(Exam exam, ExamSession session, Submission submission, LocalDateTime now) {
        if (session == null || session.getStartTime() == null) {
            return SnapshotGap.none();
        }
        LocalDateTime anchor;
        if (SessionStatus.ANSWERING.name().equals(session.getStatus())) {
            anchor = now;
        } else if (session.getEndTime() != null) {
            anchor = session.getEndTime();
        } else if (submission != null && submission.getSubmittedAt() != null) {
            anchor = submission.getSubmittedAt();
        } else if (exam != null && exam.getEndTime() != null && !exam.getEndTime().isAfter(now)) {
            anchor = exam.getEndTime();
        } else {
            anchor = now;
        }
        LocalDateTime lastSnapshotTime = session.getLastSnapshotTime() == null ? session.getStartTime() : session.getLastSnapshotTime();
        if (anchor == null || lastSnapshotTime == null || anchor.isBefore(lastSnapshotTime)) {
            return SnapshotGap.none();
        }
        long gapMs = Duration.between(lastSnapshotTime, anchor).toMillis();
        if (gapMs > 90_000L) {
            return new SnapshotGap(true, 4);
        }
        if (gapMs > 45_000L) {
            return new SnapshotGap(true, 2);
        }
        return SnapshotGap.none();
    }

    private String riskLevelOf(int riskScore) {
        if (riskScore >= 8) {
            return "HIGH";
        }
        if (riskScore >= 4) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private List<ProctoringRecentEventView> buildRecentEvents(ProctoringContext context) {
        return context.eventsByStudent.values().stream()
            .flatMap(List::stream)
            .sorted(Comparator.comparing(AntiCheatEvent::getEventTime, Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(10)
            .map(event -> {
                StudentRiskSnapshot snapshot = context.studentSnapshots.get(event.getStudentId());
                return ProctoringRecentEventView.builder()
                    .studentId(event.getStudentId())
                    .studentName(snapshot == null ? "学生" + event.getStudentId() : snapshot.studentName())
                    .username(snapshot == null ? null : snapshot.username())
                    .classNames(snapshot == null ? List.of() : snapshot.classNames())
                    .eventType(event.getEventType())
                    .eventTime(event.getEventTime())
                    .durationMs(event.getDurationMs())
                    .build();
            })
            .toList();
    }

    private List<ProctoringEventStatView> buildEventTypeStats(List<AntiCheatEvent> events) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        Map<String, List<AntiCheatEvent>> grouped = events.stream()
            .filter(event -> event.getEventType() != null)
            .collect(Collectors.groupingBy(AntiCheatEvent::getEventType, LinkedHashMap::new, Collectors.toList()));
        return grouped.entrySet().stream()
            .map(entry -> ProctoringEventStatView.builder()
                .eventType(entry.getKey())
                .count(entry.getValue().size())
                .totalDurationMs(entry.getValue().stream().mapToLong(item -> safeDuration(item.getDurationMs())).sum())
                .build())
            .sorted(Comparator.comparing(ProctoringEventStatView::getCount, Comparator.reverseOrder())
                .thenComparing(ProctoringEventStatView::getEventType))
            .toList();
    }

    private ProctoringStudentView toStudentView(StudentRiskSnapshot snapshot) {
        return ProctoringStudentView.builder()
            .studentId(snapshot.studentId())
            .studentName(snapshot.studentName())
            .username(snapshot.username())
            .classNames(snapshot.classNames())
            .riskScore(snapshot.riskScore())
            .riskLevel(snapshot.riskLevel())
            .eventCount(snapshot.eventCount())
            .latestEventType(snapshot.latestEventType())
            .lastEventTime(snapshot.lastEventTime())
            .lastSnapshotTime(snapshot.lastSnapshotTime())
            .answering(snapshot.answering())
            .snapshotAlert(snapshot.snapshotAlert())
            .totalOffscreenDurationMs(snapshot.totalOffscreenDurationMs())
            .longOffscreen(snapshot.longOffscreen())
            .build();
    }

    private void ensureExamManagePermission(Exam exam, Collection<TeachingClass> targetClasses) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Set<String> roleCodes = new HashSet<>(userMapper.selectRoleCodes(currentUserId));
        if (roleCodes.contains(ROLE_ADMIN)) {
            return;
        }
        if (roleCodes.contains(ROLE_TEACHER)) {
            if (Objects.equals(exam.getPublisherId(), currentUserId)) {
                return;
            }
            boolean ownsTargetClass = targetClasses != null && targetClasses.stream()
                .filter(Objects::nonNull)
                .anyMatch(item -> Objects.equals(item.getTeacherId(), currentUserId));
            if (ownsTargetClass) {
                return;
            }
        }
        throw new BusinessException("无权限查看该考试监考信息");
    }

    private ExamSession pickLatestSession(ExamSession left, ExamSession right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        LocalDateTime leftAnchor = left.getUpdateTime() == null ? left.getCreateTime() : left.getUpdateTime();
        LocalDateTime rightAnchor = right.getUpdateTime() == null ? right.getCreateTime() : right.getUpdateTime();
        if (leftAnchor == null && rightAnchor == null) {
            return Objects.compare(left.getId(), right.getId(), Long::compareTo) >= 0 ? left : right;
        }
        if (leftAnchor == null) {
            return right;
        }
        if (rightAnchor == null) {
            return left;
        }
        return leftAnchor.isAfter(rightAnchor) ? left : right;
    }

    private Submission pickLatestSubmission(Submission left, Submission right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        LocalDateTime leftAnchor = left.getSubmittedAt() == null ? left.getUpdateTime() : left.getSubmittedAt();
        LocalDateTime rightAnchor = right.getSubmittedAt() == null ? right.getUpdateTime() : right.getSubmittedAt();
        if (leftAnchor == null && rightAnchor == null) {
            return Objects.compare(left.getId(), right.getId(), Long::compareTo) >= 0 ? left : right;
        }
        if (leftAnchor == null) {
            return right;
        }
        if (rightAnchor == null) {
            return left;
        }
        return leftAnchor.isAfter(rightAnchor) ? left : right;
    }

    private long safeDuration(Long durationMs) {
        return durationMs == null || durationMs < 0 ? 0L : durationMs;
    }

    private String coalesce(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private record SnapshotGap(boolean alert, int riskPoints) {

        private static SnapshotGap none() {
            return new SnapshotGap(false, 0);
        }
    }

    private record StudentRiskSnapshot(Long studentId,
                                       String studentName,
                                       String username,
                                       List<String> classNames,
                                       Integer riskScore,
                                       String riskLevel,
                                       Integer eventCount,
                                       String latestEventType,
                                       LocalDateTime lastEventTime,
                                       LocalDateTime lastSnapshotTime,
                                       boolean answering,
                                       boolean snapshotAlert,
                                       Long totalOffscreenDurationMs,
                                       boolean longOffscreen) {
    }

    private record ProctoringContext(Exam exam,
                                     Map<Long, StudentRiskSnapshot> studentSnapshots,
                                     Map<Long, List<AntiCheatEvent>> eventsByStudent) {
    }
}
