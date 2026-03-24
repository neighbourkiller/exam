package com.ekusys.exam.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ekusys.exam.admin.dto.RoleCreateRequest;
import com.ekusys.exam.admin.dto.RoleView;
import com.ekusys.exam.admin.dto.UserCreateRequest;
import com.ekusys.exam.admin.dto.UserQueryRequest;
import com.ekusys.exam.admin.dto.UserUpdateRequest;
import com.ekusys.exam.admin.dto.UserView;
import com.ekusys.exam.common.api.PageResponse;
import com.ekusys.exam.common.exception.BusinessException;
import com.ekusys.exam.repository.entity.ClassStudent;
import com.ekusys.exam.repository.entity.Role;
import com.ekusys.exam.repository.entity.User;
import com.ekusys.exam.repository.entity.UserRole;
import com.ekusys.exam.repository.mapper.ClassStudentMapper;
import com.ekusys.exam.repository.mapper.RoleMapper;
import com.ekusys.exam.repository.mapper.UserMapper;
import com.ekusys.exam.repository.mapper.UserRoleMapper;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final ClassStudentMapper classStudentMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.security.default-password:123456}")
    private String defaultPassword;

    public AdminService(UserMapper userMapper,
                        RoleMapper roleMapper,
                        UserRoleMapper userRoleMapper,
                        ClassStudentMapper classStudentMapper,
                        PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.classStudentMapper = classStudentMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public PageResponse<UserView> queryUsers(UserQueryRequest request) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
            wrapper.lambda().like(User::getUsername, request.getKeyword()).or().like(User::getRealName, request.getKeyword());
        }
        Page<User> page = userMapper.selectPage(new Page<>(request.getPageNum(), request.getPageSize()), wrapper);
        List<Role> roles = roleMapper.selectList(null);
        Map<Long, Role> roleMap = roles.stream().collect(Collectors.toMap(Role::getId, e -> e));

        List<UserView> users = page.getRecords().stream().map(user -> {
            List<UserRole> userRoles = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, user.getId()));
            List<RoleView> roleViews = userRoles.stream()
                .map(UserRole::getRoleId)
                .map(roleMap::get)
                .filter(r -> r != null)
                .map(r -> RoleView.builder().id(r.getId()).code(r.getCode()).name(r.getName()).build())
                .toList();

            ClassStudent classStudent = classStudentMapper.selectOne(
                new LambdaQueryWrapper<ClassStudent>().eq(ClassStudent::getStudentId, user.getId()).last("limit 1")
            );
            return UserView.builder()
                .id(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .enabled(user.getEnabled())
                .classId(classStudent == null ? null : classStudent.getClassId())
                .roles(roleViews)
                .build();
        }).toList();

        return PageResponse.<UserView>builder()
            .pageNum(page.getCurrent())
            .pageSize(page.getSize())
            .total(page.getTotal())
            .records(users)
            .build();
    }

    @Transactional
    public Long createUser(UserCreateRequest request) {
        User existing = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));
        if (existing != null) {
            throw new BusinessException("用户名已存在");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setRealName(request.getRealName());
        user.setEnabled(true);
        user.setPassword(passwordEncoder.encode(request.getPassword() == null || request.getPassword().isBlank()
            ? defaultPassword : request.getPassword()));
        userMapper.insert(user);

        assignRoles(user.getId(), request.getRoleIds());
        updateStudentClass(user.getId(), request.getClassId());
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
        if (request.getClassId() != null) {
            updateStudentClass(userId, request.getClassId());
        }
    }

    @Transactional
    public void deleteUser(Long userId) {
        ensureUser(userId);
        userMapper.deleteById(userId);
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId));
        classStudentMapper.delete(new LambdaQueryWrapper<ClassStudent>().eq(ClassStudent::getStudentId, userId));
    }

    @Transactional
    public void resetPassword(Long userId, String password) {
        User user = ensureUser(userId);
        user.setPassword(passwordEncoder.encode(password));
        userMapper.updateById(user);
    }

    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        ensureUser(userId);
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId));
        for (Long roleId : roleIds) {
            Role role = roleMapper.selectById(roleId);
            if (role == null) {
                throw new BusinessException("角色不存在: " + roleId);
            }
            UserRole ur = new UserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            userRoleMapper.insert(ur);
        }
    }

    public List<RoleView> listRoles() {
        return roleMapper.selectList(null).stream()
            .map(role -> RoleView.builder().id(role.getId()).code(role.getCode()).name(role.getName()).build())
            .toList();
    }

    public Long createRole(RoleCreateRequest request) {
        Role role = new Role();
        role.setCode(request.getCode());
        role.setName(request.getName());
        roleMapper.insert(role);
        return role.getId();
    }

    private void updateStudentClass(Long userId, Long classId) {
        classStudentMapper.delete(new LambdaQueryWrapper<ClassStudent>().eq(ClassStudent::getStudentId, userId));
        if (classId != null) {
            ClassStudent cs = new ClassStudent();
            cs.setClassId(classId);
            cs.setStudentId(userId);
            classStudentMapper.insert(cs);
        }
    }

    private User ensureUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }
}
