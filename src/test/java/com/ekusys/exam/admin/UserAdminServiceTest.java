package com.ekusys.exam.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ekusys.exam.admin.dto.UserCreateRequest;
import com.ekusys.exam.admin.dto.UserUpdateRequest;
import com.ekusys.exam.admin.service.RoleAdminService;
import com.ekusys.exam.admin.service.UserAdminService;
import com.ekusys.exam.admin.service.UserProfileSyncService;
import com.ekusys.exam.common.exception.BusinessException;
import com.ekusys.exam.repository.entity.User;
import com.ekusys.exam.repository.mapper.UserMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RoleAdminService roleAdminService;
    @Mock
    private UserProfileSyncService userProfileSyncService;

    private UserAdminService userAdminService;

    @BeforeEach
    void setUp() {
        userAdminService = new UserAdminService(userMapper, passwordEncoder, roleAdminService, userProfileSyncService);
        ReflectionTestUtils.setField(userAdminService, "defaultPassword", "123456");
    }

    @Test
    void createUserShouldRejectDuplicateUsername() {
        User existing = new User();
        existing.setId(1L);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        UserCreateRequest request = new UserCreateRequest();
        request.setUsername("alice");

        BusinessException ex = assertThrows(BusinessException.class, () -> userAdminService.createUser(request));
        assertEquals("用户名已存在", ex.getMessage());
    }

    @Test
    void createUserShouldUseDefaultPasswordAndSyncStudentProfile() {
        when(passwordEncoder.encode("123456")).thenReturn("encoded");
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(roleAdminService.listRoleCodesByUserId(1001L)).thenReturn(List.of("STUDENT"));
        org.mockito.Mockito.doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1001L);
            return 1;
        }).when(userMapper).insert(any(User.class));

        UserCreateRequest request = new UserCreateRequest();
        request.setUsername("alice");
        request.setRealName("Alice");
        request.setRoleIds(List.of(3L));
        request.setTeachingClassIds(List.of(3301L));
        request.setStudentNo("S001");

        Long userId = userAdminService.createUser(request);

        assertEquals(1001L, userId);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        assertEquals("encoded", captor.getValue().getPassword());
        verify(roleAdminService).assignRoles(1001L, List.of(3L), List.of(3301L));
        verify(userProfileSyncService).syncStudentNo(1001L, "S001");
    }

    @Test
    void updateUserShouldSyncStudentNoAndTeachingClasses() {
        User user = new User();
        user.setId(1001L);
        user.setRealName("Old");
        user.setEnabled(true);
        when(userMapper.selectById(1001L)).thenReturn(user);
        when(roleAdminService.listRoleCodesByUserId(1001L)).thenReturn(List.of("STUDENT"));

        UserUpdateRequest request = new UserUpdateRequest();
        request.setRealName("New");
        request.setEnabled(false);
        request.setStudentNo("S002");
        request.setTeachingClassIds(List.of(3302L));

        userAdminService.updateUser(1001L, request);

        verify(userMapper).updateById(user);
        assertEquals("New", user.getRealName());
        assertEquals(false, user.getEnabled());
        verify(userProfileSyncService).syncStudentNo(1001L, "S002");
        verify(userProfileSyncService).updateStudentTeachingClasses(1001L, List.of(3302L), List.of("STUDENT"));
    }
}
