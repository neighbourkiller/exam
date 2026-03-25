package com.ekusys.exam.admin.controller;

import com.ekusys.exam.admin.dto.AssignRolesRequest;
import com.ekusys.exam.admin.dto.CourseCreateRequest;
import com.ekusys.exam.admin.dto.CourseUpdateRequest;
import com.ekusys.exam.admin.dto.CourseView;
import com.ekusys.exam.admin.dto.ResetPasswordRequest;
import com.ekusys.exam.admin.dto.RoleCreateRequest;
import com.ekusys.exam.admin.dto.RoleView;
import com.ekusys.exam.admin.dto.UserCreateRequest;
import com.ekusys.exam.admin.dto.UserQueryRequest;
import com.ekusys.exam.admin.dto.UserUpdateRequest;
import com.ekusys.exam.admin.dto.UserView;
import com.ekusys.exam.admin.service.AdminService;
import com.ekusys.exam.common.api.ApiResponse;
import com.ekusys.exam.common.api.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping("/users/query")
    public ApiResponse<PageResponse<UserView>> queryUsers(@RequestBody UserQueryRequest request) {
        return ApiResponse.ok(adminService.queryUsers(request));
    }

    @PostMapping("/users")
    public ApiResponse<Long> createUser(@Valid @RequestBody UserCreateRequest request) {
        return ApiResponse.ok("创建成功", adminService.createUser(request));
    }

    @PutMapping("/users/{userId}")
    public ApiResponse<Void> updateUser(@PathVariable Long userId, @Valid @RequestBody UserUpdateRequest request) {
        adminService.updateUser(userId, request);
        return ApiResponse.ok("更新成功", null);
    }

    @DeleteMapping("/users/{userId}")
    public ApiResponse<Void> deleteUser(@PathVariable Long userId) {
        adminService.deleteUser(userId);
        return ApiResponse.ok("删除成功", null);
    }

    @PostMapping("/users/{userId}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable Long userId, @Valid @RequestBody ResetPasswordRequest request) {
        adminService.resetPassword(userId, request.getPassword());
        return ApiResponse.ok("重置成功", null);
    }

    @PutMapping("/users/{userId}/roles")
    public ApiResponse<Void> assignRoles(@PathVariable Long userId, @Valid @RequestBody AssignRolesRequest request) {
        adminService.assignRoles(userId, request.getRoleIds());
        return ApiResponse.ok("分配成功", null);
    }

    @GetMapping("/roles")
    public ApiResponse<List<RoleView>> listRoles() {
        return ApiResponse.ok(adminService.listRoles());
    }

    @PostMapping("/roles")
    public ApiResponse<Long> createRole(@Valid @RequestBody RoleCreateRequest request) {
        return ApiResponse.ok("创建成功", adminService.createRole(request));
    }

    @GetMapping("/courses")
    public ApiResponse<List<CourseView>> listCourses() {
        return ApiResponse.ok(adminService.listCourses());
    }

    @PostMapping("/courses")
    public ApiResponse<Long> createCourse(@Valid @RequestBody CourseCreateRequest request) {
        return ApiResponse.ok("创建成功", adminService.createCourse(request));
    }

    @PutMapping("/courses/{courseId}")
    public ApiResponse<Void> updateCourse(@PathVariable Long courseId, @Valid @RequestBody CourseUpdateRequest request) {
        adminService.updateCourse(courseId, request);
        return ApiResponse.ok("更新成功", null);
    }
}