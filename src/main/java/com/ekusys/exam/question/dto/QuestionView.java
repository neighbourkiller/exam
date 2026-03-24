package com.ekusys.exam.question.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QuestionView {

    private Long id;
    private Long subjectId;
    private String type;
    private String difficulty;
    private String content;
    private String optionsJson;
    private String answer;
    private String analysis;
    private Integer defaultScore;
}
