package com.ekusys.exam.exam.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StudentExamQuestionView {

    private Long questionId;
    private String type;
    private String content;
    private String optionsJson;
    private Integer score;
    private Integer sortOrder;
    private String currentAnswer;
}
