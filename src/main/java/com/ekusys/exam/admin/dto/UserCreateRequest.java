package com.ekusys.exam.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

@Data
public class UserCreateRequest {

    @NotBlank
    private String username;

    @NotBlank
    private String realName;

    private String password;

    @NotEmpty
    private List<Long> roleIds;

    private Long classId;
}
