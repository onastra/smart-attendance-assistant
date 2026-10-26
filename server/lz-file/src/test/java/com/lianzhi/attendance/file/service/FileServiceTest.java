package com.lianzhi.attendance.file.service;

import com.lianzhi.attendance.common.exception.BusinessException;
import com.lianzhi.attendance.file.entity.SysFile;
import com.lianzhi.attendance.file.mapper.FileMapper;
import com.lianzhi.attendance.file.mapper.FileSupportMapper;
import com.lianzhi.attendance.file.storage.LocalFileStore;
import com.lianzhi.attendance.framework.auth.AuthUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 文件可见范围鉴权的单元测试。
 *
 * <p>这是"受控存储"的真正含义所在：下载接口只接受 id，能不能下载由可见范围决定。
 * 若这套判定写错，就等于把全库文件对任何持 token 的人开放——属于越权读取，
 * 因此三档可见范围 + 管理员豁免 + 退化规则都要逐一断言。</p>
 */
@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    private static final Long UPLOADER = 100L;
    private static final Long OTHER_USER = 200L;
    private static final Long DEPT_A = 10L;
    private static final Long DEPT_B = 20L;

    @Mock
    private FileMapper fileMapper;
    @Mock
    private FileSupportMapper supportMapper;
    @Mock
    private LocalFileStore fileStore;

    private FileService fileService;

    @BeforeEach
    void setUp() {
        fileService = new FileService(fileMapper, supportMapper, fileStore);
        ReflectionTestUtils.setField(fileService, "maxSizeBytes", 10 * 1024 * 1024L);
    }

    // ---------- 三档可见范围 ----------

    @Test
    @DisplayName("PUBLIC 文件：任何登录用户都可下载")
    void publicFileIsDownloadableByAnyone() {
        SysFile file = file(SysFile.VISIBILITY_PUBLIC, UPLOADER, null);

        assertTrue(fileService.canDownload(file, user(OTHER_USER, 999L, Set.of())));
        assertTrue(fileService.canDownload(file, user(UPLOADER, 888L, Set.of())));
    }

    @Test
    @DisplayName("PRIVATE 文件：仅上传人可下载，其他人一律拒绝")
    void privateFileOnlyForUploader() {
        SysFile file = file(SysFile.VISIBILITY_PRIVATE, UPLOADER, null);

        assertTrue(fileService.canDownload(file, user(UPLOADER, 888L, Set.of())));
        assertFalse(fileService.canDownload(file, user(OTHER_USER, 999L, Set.of())),
                "非上传人下载 PRIVATE 文件必须被拒绝");
    }

    @Test
    @DisplayName("DEPT 文件：同部门可下载")
    void deptFileIsDownloadableWithinSameDept() {
        SysFile file = file(SysFile.VISIBILITY_DEPT, UPLOADER, DEPT_A);
        AuthUser colleague = user(OTHER_USER, 999L, Set.of());

        when(supportMapper.selectEmployeeDeptId(999L)).thenReturn(DEPT_A);

        assertTrue(fileService.canDownload(file, colleague));
    }

    @Test
    @DisplayName("DEPT 文件：跨部门拒绝")
    void deptFileIsDeniedAcrossDept() {
        SysFile file = file(SysFile.VISIBILITY_DEPT, UPLOADER, DEPT_A);
        AuthUser outsider = user(OTHER_USER, 999L, Set.of());

        when(supportMapper.selectEmployeeDeptId(999L)).thenReturn(DEPT_B);

        assertFalse(fileService.canDownload(file, outsider), "跨部门不得下载 DEPT 文件");
    }

    @Test
    @DisplayName("DEPT 但没有部门归属时退化为仅上传人，绝不向全员开放")
    void deptFileWithoutDeptFallsBackToUploaderOnly() {
        SysFile file = file(SysFile.VISIBILITY_DEPT, UPLOADER, null);

        assertTrue(fileService.canDownload(file, user(UPLOADER, 888L, Set.of())));
        assertFalse(fileService.canDownload(file, user(OTHER_USER, 999L, Set.of())),
                "ownerDeptId 缺失时必须收紧为仅上传人，而不是放行所有人");
    }

    @Test
    @DisplayName("未建档用户（employeeId 为空）不能靠 DEPT 文件拿到别人的文件")
    void userWithoutEmployeeRecordCannotMatchDept() {
        SysFile file = file(SysFile.VISIBILITY_DEPT, UPLOADER, DEPT_A);
        AuthUser noProfile = user(OTHER_USER, null, Set.of());

        assertFalse(fileService.canDownload(file, noProfile));
    }

    // ---------- 管理员与异常输入 ----------

    @Test
    @DisplayName("file:admin 不受可见范围限制")
    void adminBypassesVisibility() {
        AuthUser admin = user(OTHER_USER, 999L, Set.of("file:admin"));

        assertTrue(fileService.canDownload(file(SysFile.VISIBILITY_PRIVATE, UPLOADER, null), admin));
        assertTrue(fileService.canDownload(file(SysFile.VISIBILITY_DEPT, UPLOADER, DEPT_A), admin));
    }

    @Test
    @DisplayName("文件对象或用户为空时拒绝，而不是抛出空指针")
    void nullInputIsDenied() {
        assertFalse(fileService.canDownload(null, user(UPLOADER, 1L, Set.of())));
        assertFalse(fileService.canDownload(file(SysFile.VISIBILITY_PUBLIC, UPLOADER, null), null));
    }

    @Test
    @DisplayName("可见范围字段缺失时按 PRIVATE 处理，不允许因字段为空而放行")
    void missingVisibilityIsTreatedAsPrivate() {
        SysFile file = file(null, UPLOADER, null);

        assertTrue(fileService.canDownload(file, user(UPLOADER, 888L, Set.of())));
        assertFalse(fileService.canDownload(file, user(OTHER_USER, 999L, Set.of())),
                "缺失的可见范围必须收紧，而不是默认公开");
    }

    // ---------- 上传参数校验 ----------

    @Test
    @DisplayName("上传 DEPT 文件却不带部门 ID：直接拒绝，不静默降级为公开")
    void uploadRejectsDeptWithoutDeptId() {
        MultipartFile multipart = multipart(1024);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileService.upload(multipart, "DEPT", null, user(UPLOADER, 1L, Set.of())));

        assertTrue(ex.getMessage().contains("部门"), "应指出 DEPT 需要部门，实际：" + ex.getMessage());
    }

    @Test
    @DisplayName("上传时可见范围取值非法：拒绝并列出允许值")
    void uploadRejectsUnknownVisibility() {
        MultipartFile multipart = multipart(1024);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileService.upload(multipart, "SECRET", DEPT_A, user(UPLOADER, 1L, Set.of())));

        assertTrue(ex.getMessage().contains("PUBLIC/DEPT/PRIVATE"), "实际：" + ex.getMessage());
    }

    @Test
    @DisplayName("空文件与超限文件在写盘之前就被拦截")
    void uploadRejectsEmptyAndOversizedFile() {
        MultipartFile empty = org.mockito.Mockito.mock(MultipartFile.class);
        when(empty.isEmpty()).thenReturn(true);
        BusinessException emptyEx = assertThrows(BusinessException.class,
                () -> fileService.upload(empty, "PRIVATE", null, user(UPLOADER, 1L, Set.of())));
        assertEquals("上传文件为空", emptyEx.getMessage());

        MultipartFile huge = multipart(20 * 1024 * 1024L);
        BusinessException hugeEx = assertThrows(BusinessException.class,
                () -> fileService.upload(huge, "PRIVATE", null, user(UPLOADER, 1L, Set.of())));
        assertTrue(hugeEx.getMessage().contains("超过上限"), "实际：" + hugeEx.getMessage());
    }

    // ---------- 辅助方法 ----------

    private static SysFile file(String visibility, Long uploaderId, Long ownerDeptId) {
        SysFile file = new SysFile();
        file.setId(1L);
        file.setVisibility(visibility);
        file.setUploaderId(uploaderId);
        file.setOwnerDeptId(ownerDeptId);
        return file;
    }

    private static AuthUser user(Long userId, Long employeeId, Set<String> permissions) {
        AuthUser user = new AuthUser();
        user.setUserId(userId);
        user.setEmployeeId(employeeId);
        user.setPermissions(permissions);
        return user;
    }

    private static MultipartFile multipart(long size) {
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(size);
        return file;
    }
}
