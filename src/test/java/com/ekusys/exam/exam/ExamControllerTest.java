package com.ekusys.exam.exam;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ekusys.exam.common.security.JwtAuthenticationFilter;
import com.ekusys.exam.exam.controller.ExamController;
import com.ekusys.exam.exam.dto.StartExamResponse;
import com.ekusys.exam.exam.dto.SubmitResultView;
import com.ekusys.exam.exam.service.ExamProctoringService;
import com.ekusys.exam.exam.service.ExamService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(value = ExamController.class, excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
class ExamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private ExamService examService;

    @MockitoBean
    private ExamProctoringService examProctoringService;

    @Test
    void startShouldReturnExamPayload() throws Exception {
        when(examService.startExam(1L)).thenReturn(StartExamResponse.builder()
            .examId(1L)
            .examName("Java期末")
            .durationMinutes(60)
            .startTime(LocalDateTime.of(2026, 4, 1, 10, 0))
            .endTime(LocalDateTime.of(2026, 4, 1, 11, 0))
            .questions(List.of())
            .build());

        mockMvc.perform(post("/api/v1/exams/1/start"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.examId").value(1))
            .andExpect(jsonPath("$.data.examName").value("Java期末"));
    }

    @Test
    void submitShouldReturnProcessingResult() throws Exception {
        when(examService.submit(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any())).thenReturn(
            SubmitResultView.builder()
                .submissionId(99L)
                .status("PROCESSING")
                .build()
        );

        mockMvc.perform(post("/api/v1/exams/1/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of(
                    "answers", List.of(java.util.Map.of("questionId", 1, "answerText", "A"))
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.submissionId").value(99))
            .andExpect(jsonPath("$.data.totalScore").doesNotExist())
            .andExpect(jsonPath("$.data.status").value("PROCESSING"));
    }

    @Test
    void submitShouldReturnFallbackStatusWhenSyncDowngraded() throws Exception {
        when(examService.submit(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any())).thenReturn(
            SubmitResultView.builder()
                .submissionId(100L)
                .objectiveScore(60)
                .subjectiveScore(0)
                .totalScore(60)
                .passFlag(true)
                .status("GRADED")
                .build()
        );

        mockMvc.perform(post("/api/v1/exams/1/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of(
                    "answers", List.of(java.util.Map.of("questionId", 1, "answerText", "A"))
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.submissionId").value(100))
            .andExpect(jsonPath("$.data.totalScore").value(60))
            .andExpect(jsonPath("$.data.status").value("GRADED"));
    }
}






