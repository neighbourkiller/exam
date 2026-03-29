package com.ekusys.exam.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ekusys.exam.admin.dto.CourseCreateRequest;
import com.ekusys.exam.admin.dto.CourseUpdateRequest;
import com.ekusys.exam.admin.dto.CourseView;
import com.ekusys.exam.admin.dto.RoleCreateRequest;
import com.ekusys.exam.admin.dto.RoleView;
import com.ekusys.exam.admin.dto.TeachingClassCreateRequest;
import com.ekusys.exam.admin.dto.TeachingClassUpdateRequest;
import com.ekusys.exam.admin.dto.TeachingClassView;
import com.ekusys.exam.admin.dto.UserCreateRequest;
import com.ekusys.exam.admin.dto.UserQueryRequest;
import com.ekusys.exam.admin.dto.UserUpdateRequest;
import com.ekusys.exam.admin.dto.UserView;
import com.ekusys.exam.common.api.PageResponse;
import com.ekusys.exam.common.exception.BusinessException;
import com.ekusys.exam.repository.entity.Role;
import com.ekusys.exam.repository.entity.StudentProfile;
import com.ekusys.exam.repository.entity.StudentTeachingClass;
import com.ekusys.exam.repository.entity.Subject;
import com.ekusys.exam.repository.entity.TeacherProfile;
import com.ekusys.exam.repository.entity.TeachingClass;
import com.ekusys.exam.repository.entity.User;
import com.ekusys.exam.repository.entity.UserRole;
import com.ekusys.exam.repository.mapper.RoleMapper;
import com.ekusys.exam.repository.mapper.StudentProfileMapper;
import com.ekusys.exam.repository.mapper.StudentTeachingClassMapper;
import com.ekusys.exam.repository.mapper.SubjectMapper;
import com.ekusys.exam.repository.mapper.TeacherProfileMapper;
import com.ekusys.exam.repository.mapper.TeachingClassMapper;
import com.ekusys.exam.repository.mapper.UserMapper;
import com.ekusys.exam.repository.mapper.UserRoleMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private static final String ROLE_TEACHER = "TEACHER";
    private static final String ROLE_STUDENT = "STUDENT";

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final SubjectMapper subjectMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final StudentTeachingClassMapper studentTeachingClassMapper;
    private final StudentProfileMapper studentProfileMapper;
    private final TeacherProfileMapper teacherProfileMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.security.default-password:123456}")
    private String defaultPassword;

    public AdminService(UserMapper userMapper,
                        RoleMapper roleMapper,
                        UserRoleMapper userRoleMapper,
                        SubjectMapper subjectMapper,
                        TeachingClassMapper teachingClassMapper,
                        StudentTeachingClassMapper studentTeachingClassMapper,
                        StudentProfileMapper studentProfileMapper,
                        TeacherProfileMapper teacherProfileMapper,
                        PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.subjectMapper = subjectMapper;
        this.teachingClassMapper = teachingClassMapper;
        this.studentTeachingClassMapper = studentTeachingClassMapper;
        this.studentProfileMapper = studentProfileMapper;
        this.teacherProfileMapper = teacherProfileMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public PageResponse<UserView> queryUsers(UserQueryRequest request) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
            wrapper.lambda().like(User::getUsername, request.getKeyword()).or().like(User::getRealName, request.getKeyword());
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
        List<Role> roles = roleMapper.selectList(null);
        Map<Long, Role> roleMap = roles.stream().collect(Collectors.toMap(Role::getId, e -> e));
        Map<Long, List<Long>> userRoleIdMap = userRoleMapper.selectList(
            new LambdaQueryWrapper<UserRole>().in(UserRole::getUserId, userIds)
        ).stream().collect(Collectors.groupingBy(
            UserRole::getUserId,
            Collectors.mapping(UserRole::getRoleId, Collectors.toList())
        ));

        Map<Long, List<TeachingClassView>> userTeachingClassMap = buildUserTeachingClassMap(userIds);
        Map<Long, String> studentNoMap = buildStudentNoMap(userIds);

        List<UserView> users = page.getRecords().stream().map(user -> {
            List<RoleView> roleViews = userRoleIdMap.getOrDefault(user.getId(), List.of()).stream()
                .map(roleMap::get)
                .filter(r -> r != null)
                .map(r -> RoleView.builder().id(r.getId()).code(r.getCode()).name(r.getName()).build())
                .toList();

            return UserView.builder()
                .id(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .enabled(user.getEnabled())
                .studentNo(studentNoMap.get(user.getId()))
                .teachingClasses(userTeachingClassMap.getOrDefault(user.getId(), List.of()))
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

        assignRolesInternal(user.getId(), request.getRoleIds(), request.getTeachingClassIds());
        List<String> roleCodes = listRoleCodesByUserId(user.getId());
        if (roleCodes.contains(ROLE_STUDENT)) {
            syncStudentNo(user.getId(), request.getStudentNo());
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
        List<String> roleCodes = listRoleCodesByUserId(userId);
        if (roleCodes.contains(ROLE_STUDENT)) {
            syncStudentNo(userId, request.getStudentNo());
        }
        if (request.getTeachingClassIds() != null) {
            updateStudentTeachingClasses(userId, request.getTeachingClassIds(), roleCodes);
        }
    }

    @Transactional
    public void deleteUser(Long userId) {
        ensureUser(userId);
        userMapper.deleteById(userId);
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId));
        studentTeachingClassMapper.delete(new LambdaQueryWrapper<StudentTeachingClass>().eq(StudentTeachingClass::getStudentId, userId));
        studentProfileMapper.delete(new LambdaQueryWrapper<StudentProfile>().eq(StudentProfile::getUserId, userId));
        teacherProfileMapper.delete(new LambdaQueryWrapper<TeacherProfile>().eq(TeacherProfile::getUserId, userId));
    }

    @Transactional
    public void resetPassword(Long userId, String password) {
        User user = ensureUser(userId);
        user.setPassword(passwordEncoder.encode(password));
        userMapper.updateById(user);
    }

    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        assignRolesInternal(userId, roleIds, null);
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

    public List<CourseView> listCourses() {
        return subjectMapper.selectList(new LambdaQueryWrapper<Subject>().orderByDesc(Subject::getCreateTime)).stream()
            .map(subject -> CourseView.builder()
                .id(subject.getId())
                .name(subject.getName())
                .description(subject.getDescription())
                .build())
            .toList();
    }

    @Transactional
    public Long createCourse(CourseCreateRequest request) {
        String courseName = request.getName() == null ? "" : request.getName().trim();
        Subject existingByName = subjectMapper.selectOne(new LambdaQueryWrapper<Subject>()
            .eq(Subject::getName, courseName)
            .last("limit 1"));
        if (existingByName != null) {
            throw new BusinessException("课程已存在");
        }

        if (request.getId() != null) {
            Subject existingById = subjectMapper.selectById(request.getId());
            if (existingById != null) {
                throw new BusinessException("课程ID已存在");
            }
        }

        Subject subject = new Subject();
        subject.setId(request.getId());
        subject.setName(courseName);
        subject.setDescription(request.getDescription());
        subjectMapper.insert(subject);
        return subject.getId();
    }

    @Transactional
    public void updateCourse(Long courseId, CourseUpdateRequest request) {
        Subject subject = subjectMapper.selectById(courseId);
        if (subject == null) {
            throw new BusinessException("课程不存在");
        }

        String courseName = request.getName() == null ? "" : request.getName().trim();
        Subject existingByName = subjectMapper.selectOne(new LambdaQueryWrapper<Subject>()
            .eq(Subject::getName, courseName)
            .ne(Subject::getId, courseId)
            .last("limit 1"));
        if (existingByName != null) {
            throw new BusinessException("课程名称已存在");
        }

        subject.setName(courseName);
        subject.setDescription(request.getDescription());
        subjectMapper.updateById(subject);
    }

    public List<TeachingClassView> listTeachingClasses() {
        List<TeachingClass> classes = teachingClassMapper.selectList(
            new LambdaQueryWrapper<TeachingClass>().orderByDesc(TeachingClass::getCreateTime)
        );
        return toTeachingClassViews(classes);
    }

    @Transactional
    public Long createTeachingClass(TeachingClassCreateRequest request) {
        String name = normalizeText(request.getName());
        String term = normalizeText(request.getTerm());
        String status = normalizeText(request.getStatus());
        if (status == null) {
            status = "ONGOING";
        }
        ensureTeachingClassRelation(request.getSubjectId(), request.getTeacherId());

        if (request.getId() != null && teachingClassMapper.selectById(request.getId()) != null) {
            throw new BusinessException("教学班ID已存在");
        }

        TeachingClass teachingClass = new TeachingClass();
        teachingClass.setId(request.getId());
        teachingClass.setName(name);
        teachingClass.setSubjectId(request.getSubjectId());
        teachingClass.setTeacherId(request.getTeacherId());
        teachingClass.setTerm(term);
        teachingClass.setStatus(status);
        teachingClass.setCapacity(request.getCapacity());
        teachingClassMapper.insert(teachingClass);
        return teachingClass.getId();
    }

    @Transactional
    public void updateTeachingClass(Long id, TeachingClassUpdateRequest request) {
        TeachingClass teachingClass = teachingClassMapper.selectById(id);
        if (teachingClass == null) {
            throw new BusinessException("教学班不存在");
        }
        ensureTeachingClassRelation(request.getSubjectId(), request.getTeacherId());

        String status = normalizeText(request.getStatus());
        teachingClass.setName(normalizeText(request.getName()));
        teachingClass.setSubjectId(request.getSubjectId());
        teachingClass.setTeacherId(request.getTeacherId());
        teachingClass.setTerm(normalizeText(request.getTerm()));
        teachingClass.setStatus(status == null ? "ONGOING" : status);
        teachingClass.setCapacity(request.getCapacity());
        teachingClassMapper.updateById(teachingClass);
    }

    private Map<Long, List<TeachingClassView>> buildUserTeachingClassMap(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        List<StudentTeachingClass> bindings = studentTeachingClassMapper.selectList(
            new LambdaQueryWrapper<StudentTeachingClass>()
                .in(StudentTeachingClass::getStudentId, userIds)
                .orderByAsc(StudentTeachingClass::getSubjectId, StudentTeachingClass::getTeachingClassId)
        );
        if (bindings.isEmpty()) {
            return Map.of();
        }

        Set<Long> classIds = bindings.stream().map(StudentTeachingClass::getTeachingClassId).collect(Collectors.toSet());
        Map<Long, TeachingClassView> classViewMap = toTeachingClassViews(teachingClassMapper.selectBatchIds(classIds))
            .stream()
            .collect(Collectors.toMap(TeachingClassView::getId, view -> view, (a, b) -> a));

        Map<Long, List<TeachingClassView>> result = new HashMap<>();
        for (StudentTeachingClass binding : bindings) {
            TeachingClassView view = classViewMap.get(binding.getTeachingClassId());
            if (view == null) {
                continue;
            }
            result.computeIfAbsent(binding.getStudentId(), k -> new ArrayList<>()).add(view);
        }
        return result;
    }

    private Map<Long, String> buildStudentNoMap(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return studentProfileMapper.selectList(
            new LambdaQueryWrapper<StudentProfile>().in(StudentProfile::getUserId, userIds)
        ).stream().collect(Collectors.toMap(
            StudentProfile::getUserId,
            StudentProfile::getStudentNo,
            (a, b) -> a
        ));
    }

    private List<TeachingClassView> toTeachingClassViews(List<TeachingClass> classes) {
        if (classes == null || classes.isEmpty()) {
            return List.of();
        }
        Set<Long> subjectIds = classes.stream()
            .map(TeachingClass::getSubjectId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, Subject> subjectMap = subjectIds.isEmpty()
            ? Collections.emptyMap()
            : subjectMapper.selectBatchIds(subjectIds).stream()
                .collect(Collectors.toMap(Subject::getId, s -> s, (a, b) -> a));

        Set<Long> teacherIds = classes.stream()
            .map(TeachingClass::getTeacherId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, User> teacherMap = teacherIds.isEmpty()
            ? Collections.emptyMap()
            : userMapper.selectBatchIds(teacherIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        return classes.stream()
            .sorted((a, b) -> {
                int subjectCompare = compareNullableLong(a.getSubjectId(), b.getSubjectId());
                if (subjectCompare != 0) {
                    return subjectCompare;
                }
                int termCompare = String.valueOf(a.getTerm()).compareTo(String.valueOf(b.getTerm()));
                if (termCompare != 0) {
                    return termCompare;
                }
                return compareNullableLong(a.getId(), b.getId());
            })
            .map(tc -> {
                Subject subject = subjectMap.get(tc.getSubjectId());
                User teacher = teacherMap.get(tc.getTeacherId());
                return TeachingClassView.builder()
                    .id(tc.getId())
                    .name(tc.getName())
                    .subjectId(tc.getSubjectId())
                    .subjectName(subject == null ? null : subject.getName())
                    .teacherId(tc.getTeacherId())
                    .teacherName(teacher == null ? null : teacher.getRealName())
                    .term(tc.getTerm())
                    .status(tc.getStatus())
                    .capacity(tc.getCapacity())
                    .build();
            }).toList();
    }

    private void assignRolesInternal(Long userId, List<Long> roleIds, List<Long> teachingClassIds) {
        ensureUser(userId);
        List<Long> safeRoleIds = roleIds == null ? List.of() : roleIds.stream()
            .filter(Objects::nonNull)
            .distinct()
            .toList();

        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId));
        for (Long roleId : safeRoleIds) {
            Role role = roleMapper.selectById(roleId);
            if (role == null) {
                throw new BusinessException("角色不存在: " + roleId);
            }
            UserRole ur = new UserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            userRoleMapper.insert(ur);
        }

        List<String> roleCodes = safeRoleIds.stream()
            .map(roleMapper::selectById)
            .filter(Objects::nonNull)
            .map(Role::getCode)
            .toList();
        syncProfilesByRoleCodes(userId, roleCodes);
        if (teachingClassIds != null || !roleCodes.contains(ROLE_STUDENT)) {
            updateStudentTeachingClasses(userId, teachingClassIds, roleCodes);
        }
    }

    private void updateStudentTeachingClasses(Long userId, List<Long> teachingClassIds, List<String> roleCodes) {
        boolean isStudent = roleCodes.contains(ROLE_STUDENT);
        List<Long> targetIds = teachingClassIds == null ? List.of() : teachingClassIds.stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new))
            .stream()
            .toList();

        if (!isStudent) {
            if (!targetIds.isEmpty()) {
                throw new BusinessException("仅学生角色可分配教学班");
            }
            studentTeachingClassMapper.delete(new LambdaQueryWrapper<StudentTeachingClass>().eq(StudentTeachingClass::getStudentId, userId));
            return;
        }

        studentTeachingClassMapper.delete(new LambdaQueryWrapper<StudentTeachingClass>().eq(StudentTeachingClass::getStudentId, userId));
        if (targetIds.isEmpty()) {
            return;
        }

        List<TeachingClass> classes = teachingClassMapper.selectBatchIds(targetIds);
        if (classes.size() != targetIds.size()) {
            throw new BusinessException("存在无效教学班ID");
        }
        Map<Long, TeachingClass> classMap = classes.stream()
            .collect(Collectors.toMap(TeachingClass::getId, tc -> tc, (a, b) -> a));

        Set<Long> subjectSet = new LinkedHashSet<>();
        for (Long classId : targetIds) {
            TeachingClass teachingClass = classMap.get(classId);
            if (teachingClass == null) {
                throw new BusinessException("存在无效教学班ID");
            }
            if (!subjectSet.add(teachingClass.getSubjectId())) {
                throw new BusinessException("同一课程仅可分配一个教学班");
            }
        }

        for (Long classId : targetIds) {
            TeachingClass teachingClass = classMap.get(classId);
            StudentTeachingClass stc = new StudentTeachingClass();
            stc.setStudentId(userId);
            stc.setSubjectId(teachingClass.getSubjectId());
            stc.setTeachingClassId(classId);
            stc.setEnrollStatus("ACTIVE");
            stc.setEnrolledAt(LocalDateTime.now());
            studentTeachingClassMapper.insert(stc);
        }
    }

    private void syncProfilesByRoleCodes(Long userId, List<String> roleCodes) {
        if (roleCodes.contains(ROLE_STUDENT)) {
            StudentProfile profile = studentProfileMapper.selectOne(
                new LambdaQueryWrapper<StudentProfile>().eq(StudentProfile::getUserId, userId).last("limit 1")
            );
            if (profile == null) {
                profile = new StudentProfile();
                profile.setUserId(userId);
                profile.setStatus("ACTIVE");
                studentProfileMapper.insert(profile);
            }
        } else {
            studentProfileMapper.delete(new LambdaQueryWrapper<StudentProfile>().eq(StudentProfile::getUserId, userId));
            studentTeachingClassMapper.delete(new LambdaQueryWrapper<StudentTeachingClass>().eq(StudentTeachingClass::getStudentId, userId));
        }

        if (roleCodes.contains(ROLE_TEACHER)) {
            TeacherProfile profile = teacherProfileMapper.selectOne(
                new LambdaQueryWrapper<TeacherProfile>().eq(TeacherProfile::getUserId, userId).last("limit 1")
            );
            if (profile == null) {
                profile = new TeacherProfile();
                profile.setUserId(userId);
                profile.setStatus("ACTIVE");
                teacherProfileMapper.insert(profile);
            }
        } else {
            teacherProfileMapper.delete(new LambdaQueryWrapper<TeacherProfile>().eq(TeacherProfile::getUserId, userId));
        }
    }

    private void syncStudentNo(Long userId, String studentNo) {
        StudentProfile profile = studentProfileMapper.selectOne(
            new LambdaQueryWrapper<StudentProfile>().eq(StudentProfile::getUserId, userId).last("limit 1")
        );
        if (profile == null) {
            profile = new StudentProfile();
            profile.setUserId(userId);
            profile.setStatus("ACTIVE");
            profile.setStudentNo(normalizeText(studentNo));
            studentProfileMapper.insert(profile);
            return;
        }
        profile.setStudentNo(normalizeText(studentNo));
        studentProfileMapper.updateById(profile);
    }

    private void ensureTeachingClassRelation(Long subjectId, Long teacherId) {
        if (subjectId == null || subjectMapper.selectById(subjectId) == null) {
            throw new BusinessException("课程不存在");
        }
        User teacher = userMapper.selectById(teacherId);
        if (teacher == null) {
            throw new BusinessException("教师用户不存在");
        }
        if (!userMapper.selectRoleCodes(teacherId).contains(ROLE_TEACHER)) {
            throw new BusinessException("指定用户不是教师角色");
        }
    }

    private List<String> listRoleCodesByUserId(Long userId) {
        return userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId))
            .stream()
            .map(UserRole::getRoleId)
            .map(roleMapper::selectById)
            .filter(Objects::nonNull)
            .map(Role::getCode)
            .toList();
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private int compareNullableLong(Long a, Long b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return -1;
        }
        if (b == null) {
            return 1;
        }
        return Long.compare(a, b);
    }

    private User ensureUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }
}
