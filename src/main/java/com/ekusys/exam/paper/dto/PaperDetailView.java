package com.ekusys.exam.paper.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaperDetailView {

    private Long id;
    private String name;
    private Long subjectId;
    private String description;
    private Integer totalScore;
    private List<PaperQuestionView> questions;
}
