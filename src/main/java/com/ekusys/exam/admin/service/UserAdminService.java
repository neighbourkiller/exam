package com.ekusys.exam.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ekusys.exam.admin.dto.UserCreateRequest;
import com.ekusys.exam.admin.dto.UserQueryRequest;
import com.ekusys.exam.admin.dto.UserUpdateRequest;
import com.ekusys.exam.admin.dto.UserView;
import com.ekusys.exam.common.api.PageResponse;
import com.ekusys.exam.common.exception.BusinessException;
import com.ekusys.exam.repository.entity.User;
import com.ekusys.exam.repository.mapper.UserMapper;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAdminService {

    private static final String ROLE_STUDENT = "STUDENT";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final RoleAdminService roleAdminService;
    private final UserProfileSyncService userProfileSyncService;

    @Value("${app.security.default-password:}")
    private String defaultPassword;

    public UserAdminService(UserMapper userMapper,
                            PasswordEncoder passwordEncoder,
                            RoleAdminService roleAdminService,
                            UserProfileSyncService userProfileSyncService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.roleAdminService = roleAdminService;
        this.userProfileSyncService = userProfileSyncService;
    }

    public PageResponse<UserView> queryUsers(UserQueryRequest request) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
            wrapper.lambda()
                .like(User::getUsername, request.getKeyword())
                .or()
                .like(User::getRealName, request.getKeyword());
        }
        Page<User> page = userMapper.selectPage(new Page<>(request.getPageNum(), request.getPageSize()), wrapper);
        if (page.getRecords().isEmpty()) {
            return PageResponse.<UserView>builder()
                .pageNum(page.getCurrent())
                .pageSize(page.getSize())
                .total(page.getTotal())
                .records(List.of())
                .build();
        }

        List<Long> userIds = page.getRecords().stream().map(User::getId).toList();
        Map<Long, List<com.ekusys.exam.admin.dto.RoleView>> userRoleViewMap = roleAdminService.buildUserRoleViewMap(userIds);
        Map<Long, List<com.ekusys.exam.admin.dto.TeachingClassView>> userTeachingClassMap = userProfileSyncService.buildUserTeachingClassMap(userIds);
        Map<Long, String> studentNoMap = userProfileSyncService.buildStudentNoMap(userIds);

        List<UserView> users = page.getRecords().stream().map(user -> UserView.builder()
            .id(user.getId())
            .username(user.getUsername())
            .realName(user.getRealName())
            .enabled(user.getEnabled())
            .studentNo(studentNoMap.get(user.getId()))
            .teachingClasses(userTeachingClassMap.getOrDefault(user.getId(), List.of()))
            .roles(userRoleViewMap.getOrDefault(user.getId(), List.of()))
            .build()).toList();

        return PageResponse.<UserView>builder()
            .pageNum(page.getCurrent())
            .pageSize(page.getSize())
            .total(page.getTotal())
            .records(users)
            .build();
    }

    @Transactional
    public Long createUser(UserCreateRequest request) {
        ensureUsernameAvailable(request.getUsername());

        User user = new User();
        user.setUsername(request.getUsername());
        user.setRealName(request.getRealName());
        user.setEnabled(true);
        String rawPassword = resolvePassword(request.getPassword());
        user.setPassword(passwordEncoder.encode(rawPassword));
        userMapper.insert(user);

        roleAdminService.assignRoles(user.getId(), request.getRoleIds(), request.getTeachingClassIds());
        List<String> roleCodes = roleAdminService.listRoleCodesByUserId(user.getId());
        if (roleCodes.contains(ROLE_STUDENT)) {
            userProfileSyncService.syncStudentNo(user.getId(), request.getStudentNo());
        }
        return user.getId();
    }

    @Transactional
    public void updateUser(Long userId, UserUpdateRequest request) {
        User user = ensureUser(userId);
        user.setRealName(request.getRealName());
        if (request.getEnabled() != null) {
            user.setEnabled(request.getEnabled());
        }
        userMapper.updateById(user);

        List<String> roleCodes = roleAdminService.listRoleCodesByUserId(userId);
        if (roleCodes.contains(ROLE_STUDENT)) {
            userProfileSyncService.syncStudentNo(userId, request.getStudentNo());
        }
        if (request.getTeachingClassIds() != null) {
            userProfileSyncService.updateStudentTeachingClasses(userId, request.getTeachingClassIds(), roleCodes);
        }
    }

    @Transactional
    public void deleteUser(Long userId) {
        ensureUser(userId);
        userMapper.deleteById(userId);
        roleAdminService.deleteUserRoles(userId);
        userProfileSyncService.deleteProfilesAndTeachingClasses(userId);
    }

    @Transactional
    public void resetPassword(Long userId, String password) {
        if (password == null || password.isBlank()) {
            throw new BusinessException("密码不能为空");
        }
        User user = ensureUser(userId);
        user.setPassword(passwordEncoder.encode(password));
        userMapper.updateById(user);
    }

    public void validateCreateUser(UserCreateRequest request) {
        ensureUsernameAvailable(request.getUsername());
        resolvePassword(request.getPassword());
        roleAdminService.validateRoleAssignment(request.getRoleIds(), request.getTeachingClassIds());
    }

    private void ensureUsernameAvailable(String username) {
        User existing = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (existing != null) {
            throw new BusinessException("用户名已存在");
        }
    }

    private User ensureUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    private String resolvePassword(String password) {
        if (password != null && !password.isBlank()) {
            return password;
        }
        if (defaultPassword != null && !defaultPassword.isBlank()) {
            return defaultPassword;
        }
        throw new BusinessException("请填写密码或配置 APP_DEFAULT_PASSWORD");
    }
}
