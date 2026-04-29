package com.ekusys.exam.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ekusys.exam.admin.dto.BulkImportResultView;
import com.ekusys.exam.admin.dto.BulkUserOperationRequest;
import com.ekusys.exam.admin.service.AdminBulkService;
import com.ekusys.exam.admin.service.AdminCsvImportService;
import com.ekusys.exam.admin.service.RoleAdminService;
import com.ekusys.exam.admin.service.TeachingClassAdminService;
import com.ekusys.exam.admin.service.UserAdminService;
import com.ekusys.exam.admin.service.UserProfileSyncService;
import com.ekusys.exam.common.exception.BusinessException;
import com.ekusys.exam.exam.service.ExamService;
import com.ekusys.exam.repository.entity.Paper;
import com.ekusys.exam.repository.entity.Role;
import com.ekusys.exam.repository.entity.TeachingClass;
import com.ekusys.exam.repository.mapper.PaperMapper;
import com.ekusys.exam.repository.mapper.RoleMapper;
import com.ekusys.exam.repository.mapper.TeachingClassMapper;
import com.ekusys.exam.repository.mapper.UserMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class AdminBulkServiceTest {

    @Mock
    private UserAdminService userAdminService;
    @Mock
    private RoleAdminService roleAdminService;
    @Mock
    private UserProfileSyncService userProfileSyncService;
    @Mock
    private TeachingClassAdminService teachingClassAdminService;
    @Mock
    private ExamService examService;
    @Mock
    private UserMapper userMapper;
    @Mock
    private PaperMapper paperMapper;
    @Mock
    private RoleMapper roleMapper;
    @Mock
    private TeachingClassMapper teachingClassMapper;

    private AdminBulkService adminBulkService;

    @BeforeEach
    void setUp() {
        adminBulkService = new AdminBulkService(
            new AdminCsvImportService(),
            userAdminService,
            roleAdminService,
            userProfileSyncService,
            teachingClassAdminService,
            examService,
            userMapper,
            paperMapper,
            roleMapper,
            teachingClassMapper
        );
    }

    @Test
    void dryRunUserImportShouldUseCreateValidation() {
        Role role = new Role();
        role.setId(3L);
        role.setCode("STUDENT");
        when(roleMapper.selectOne(any())).thenReturn(role);
        org.mockito.Mockito.doThrow(new BusinessException("请填写密码或配置 APP_DEFAULT_PASSWORD"))
            .when(userAdminService).validateCreateUser(any());

        MockMultipartFile file = csvFile("students.csv", "username,realName\ns001,张三\n");

        BulkImportResultView result = adminBulkService.importUsers(file, "STUDENT", true);

        assertEquals(1, result.getFailureCount());
        assertEquals("请填写密码或配置 APP_DEFAULT_PASSWORD", result.getErrors().getFirst().getMessage());
        verify(userAdminService).validateCreateUser(any());
        verify(userAdminService, never()).createUser(any());
    }

    @Test
    void dryRunExamImportShouldRejectPaperClassSubjectMismatch() {
        Paper paper = new Paper();
        paper.setId(10L);
        paper.setSubjectId(5001L);
        when(paperMapper.selectById(10L)).thenReturn(paper);
        TeachingClass teachingClass = new TeachingClass();
        teachingClass.setId(3301L);
        teachingClass.setSubjectId(5002L);
        when(teachingClassMapper.selectBatchIds(List.of(3301L))).thenReturn(List.of(teachingClass));
        MockMultipartFile file = csvFile(
            "exams.csv",
            "name,paperId,startTime,endTime,durationMinutes,passScore,targetClassIds\n"
                + "Java期中,10,2026-05-01 09:00:00,2026-05-01 11:00:00,120,60,3301\n"
        );

        BulkImportResultView result = adminBulkService.importExamSchedules(file, true);

        assertEquals(1, result.getFailureCount());
        assertEquals("目标教学班课程与试卷课程不一致", result.getErrors().getFirst().getMessage());
        verify(examService, never()).createExam(any());
    }

    @Test
    void operateUsersResetPasswordShouldRejectBlankPassword() {
        BulkUserOperationRequest request = new BulkUserOperationRequest();
        request.setUserIds(List.of(1001L));
        request.setAction("RESET_PASSWORD");
        request.setPassword(" ");

        BusinessException ex = assertThrows(BusinessException.class, () -> adminBulkService.operateUsers(request));

        assertEquals("密码不能为空", ex.getMessage());
        verify(userAdminService, never()).resetPassword(any(), any());
    }

    private MockMultipartFile csvFile(String filename, String content) {
        return new MockMultipartFile("file", filename, "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }
}
