package com.ekusys.exam.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserUpdateRequest {

    @NotBlank
    private String realName;

    private Boolean enabled;

    private Long classId;
}
