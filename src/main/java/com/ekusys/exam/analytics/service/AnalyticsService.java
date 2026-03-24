package com.ekusys.exam.analytics.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ekusys.exam.analytics.dto.ClassTrendItem;
import com.ekusys.exam.analytics.dto.ScoreDistributionItem;
import com.ekusys.exam.analytics.dto.WrongTopicItem;
import com.ekusys.exam.repository.entity.ClassRoom;
import com.ekusys.exam.repository.entity.ClassStudent;
import com.ekusys.exam.repository.entity.Question;
import com.ekusys.exam.repository.entity.Submission;
import com.ekusys.exam.repository.entity.SubmissionAnswer;
import com.ekusys.exam.repository.mapper.ClassRoomMapper;
import com.ekusys.exam.repository.mapper.ClassStudentMapper;
import com.ekusys.exam.repository.mapper.QuestionMapper;
import com.ekusys.exam.repository.mapper.SubmissionAnswerMapper;
import com.ekusys.exam.repository.mapper.SubmissionMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsService {

    private final SubmissionMapper submissionMapper;
    private final SubmissionAnswerMapper submissionAnswerMapper;
    private final ClassStudentMapper classStudentMapper;
    private final ClassRoomMapper classRoomMapper;
    private final QuestionMapper questionMapper;

    public AnalyticsService(SubmissionMapper submissionMapper,
                            SubmissionAnswerMapper submissionAnswerMapper,
                            ClassStudentMapper classStudentMapper,
                            ClassRoomMapper classRoomMapper,
                            QuestionMapper questionMapper) {
        this.submissionMapper = submissionMapper;
        this.submissionAnswerMapper = submissionAnswerMapper;
        this.classStudentMapper = classStudentMapper;
        this.classRoomMapper = classRoomMapper;
        this.questionMapper = questionMapper;
    }

    public List<ScoreDistributionItem> scoreDistribution(Long examId) {
        List<Submission> submissions = submissionMapper.selectList(
            new LambdaQueryWrapper<Submission>().eq(Submission::getExamId, examId)
        );
        int[] buckets = new int[5];
        for (Submission submission : submissions) {
            int score = submission.getTotalScore() == null ? 0 : submission.getTotalScore();
            if (score < 60) {
                buckets[0]++;
            } else if (score < 70) {
                buckets[1]++;
            } else if (score < 80) {
                buckets[2]++;
            } else if (score < 90) {
                buckets[3]++;
            } else {
                buckets[4]++;
            }
        }
        return List.of(
            ScoreDistributionItem.builder().range("0-59").count(buckets[0]).build(),
            ScoreDistributionItem.builder().range("60-69").count(buckets[1]).build(),
            ScoreDistributionItem.builder().range("70-79").count(buckets[2]).build(),
            ScoreDistributionItem.builder().range("80-89").count(buckets[3]).build(),
            ScoreDistributionItem.builder().range("90-100").count(buckets[4]).build()
        );
    }

    public List<ClassTrendItem> classTrend(Long examId) {
        List<Submission> submissions = submissionMapper.selectList(new LambdaQueryWrapper<Submission>().eq(Submission::getExamId, examId));
        if (submissions.isEmpty()) {
            return List.of();
        }

        Map<Long, Long> studentClassMap = new HashMap<>();
        for (Submission submission : submissions) {
            ClassStudent cs = classStudentMapper.selectOne(new LambdaQueryWrapper<ClassStudent>()
                .eq(ClassStudent::getStudentId, submission.getStudentId()).last("limit 1"));
            if (cs != null) {
                studentClassMap.put(submission.getStudentId(), cs.getClassId());
            }
        }

        Map<Long, List<Integer>> classScores = new HashMap<>();
        for (Submission submission : submissions) {
            Long classId = studentClassMap.get(submission.getStudentId());
            if (classId == null) {
                continue;
            }
            classScores.computeIfAbsent(classId, k -> new ArrayList<>()).add(submission.getTotalScore() == null ? 0 : submission.getTotalScore());
        }

        List<ClassTrendItem> items = new ArrayList<>();
        for (Map.Entry<Long, List<Integer>> entry : classScores.entrySet()) {
            ClassRoom classroom = classRoomMapper.selectById(entry.getKey());
            double avg = entry.getValue().stream().mapToInt(Integer::intValue).average().orElse(0);
            items.add(ClassTrendItem.builder()
                .classId(entry.getKey())
                .className(classroom == null ? "未知班级" : classroom.getName())
                .avgScore(Math.round(avg * 100.0) / 100.0)
                .build());
        }
        items.sort(Comparator.comparing(ClassTrendItem::getClassId));
        return items;
    }

    public List<WrongTopicItem> wrongTopics(Long examId) {
        List<Submission> submissions = submissionMapper.selectList(new LambdaQueryWrapper<Submission>().eq(Submission::getExamId, examId));
        if (submissions.isEmpty()) {
            return List.of();
        }
        List<Long> submissionIds = submissions.stream().map(Submission::getId).toList();
        List<SubmissionAnswer> answers = submissionAnswerMapper.selectList(new LambdaQueryWrapper<SubmissionAnswer>()
            .in(SubmissionAnswer::getSubmissionId, submissionIds)
            .isNotNull(SubmissionAnswer::getObjectiveCorrect));

        Map<Long, Integer> total = new HashMap<>();
        Map<Long, Integer> wrong = new HashMap<>();

        for (SubmissionAnswer answer : answers) {
            Long qid = answer.getQuestionId();
            total.put(qid, total.getOrDefault(qid, 0) + 1);
            if (Boolean.FALSE.equals(answer.getObjectiveCorrect())) {
                wrong.put(qid, wrong.getOrDefault(qid, 0) + 1);
            }
        }

        List<WrongTopicItem> items = new ArrayList<>();
        for (Long qid : total.keySet()) {
            int t = total.getOrDefault(qid, 0);
            int w = wrong.getOrDefault(qid, 0);
            Question question = questionMapper.selectById(qid);
            double rate = t == 0 ? 0 : (w * 1.0 / t);
            items.add(WrongTopicItem.builder()
                .questionId(qid)
                .questionContent(question == null ? "" : question.getContent())
                .wrongRate(Math.round(rate * 10000.0) / 100.0)
                .wrongCount(w)
                .totalCount(t)
                .build());
        }

        return items.stream()
            .sorted((a, b) -> Double.compare(b.getWrongRate(), a.getWrongRate()))
            .limit(10)
            .toList();
    }
}
