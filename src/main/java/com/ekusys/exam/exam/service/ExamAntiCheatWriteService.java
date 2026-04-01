package com.ekusys.exam.exam.service;

import com.ekusys.exam.exam.dto.AntiCheatEventRequest;
import com.ekusys.exam.repository.entity.AntiCheatEvent;
import com.ekusys.exam.repository.mapper.AntiCheatEventMapper;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExamAntiCheatWriteService {

    private static final Logger log = LoggerFactory.getLogger(ExamAntiCheatWriteService.class);

    private final ExamAccessService examAccessService;
    private final AntiCheatEventMapper antiCheatEventMapper;

    public ExamAntiCheatWriteService(ExamAccessService examAccessService,
                                     AntiCheatEventMapper antiCheatEventMapper) {
        this.examAccessService = examAccessService;
        this.antiCheatEventMapper = antiCheatEventMapper;
    }

    @Transactional
    public void record(Long examId, AntiCheatEventRequest request) {
        Long studentId = examAccessService.getCurrentUserId();
        examAccessService.checkStudentAccess(examId, studentId);

        AntiCheatEvent event = new AntiCheatEvent();
        event.setExamId(examId);
        event.setStudentId(studentId);
        event.setEventType(request.getEventType());
        event.setDurationMs(request.getDurationMs());
        event.setPayload(request.getPayload());
        event.setEventTime(LocalDateTime.now());
        antiCheatEventMapper.insert(event);
        log.info("Anti-cheat event recorded: examId={}, studentId={}, eventType={}",
            examId, studentId, request.getEventType());
    }
}
