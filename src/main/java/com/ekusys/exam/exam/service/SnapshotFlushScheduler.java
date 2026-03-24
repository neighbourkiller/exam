package com.ekusys.exam.exam.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SnapshotFlushScheduler {

    private final ExamService examService;

    public SnapshotFlushScheduler(ExamService examService) {
        this.examService = examService;
    }

    @Scheduled(fixedDelayString = "${app.snapshot.flush-interval-ms:30000}")
    public void flushSnapshots() {
        examService.flushAllSnapshotsToDatabase();
    }
}
