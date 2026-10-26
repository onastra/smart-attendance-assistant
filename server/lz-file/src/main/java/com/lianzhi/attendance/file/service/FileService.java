package com.lianzhi.attendance.file.service;

import com.lianzhi.attendance.common.exception.BusinessException;
import com.lianzhi.attendance.common.result.ResultCode;
import com.lianzhi.attendance.file.entity.SysFile;
import com.lianzhi.attendance.file.mapper.FileMapper;
import com.lianzhi.attendance.file.mapper.FileSupportMapper;
import com.lianzhi.attendance.file.storage.LocalFileStore;
import com.lianzhi.attendance.framework.auth.AuthUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * 文件服务：受控存储 + 鉴权下载。
 *
 * <p>阶段 6 之前的状态是"文件只进不出"——知识库上传确实往 sys_file 写了记录，
 * 但没有任何下载接口，记录成了死数据。这里把下载补齐，并且是按可见范围鉴权的下载，
 * 而不是"知道 id 就能拿到文件"。</p>
 *
 * <p>鉴权口径（与知识库文档的三档可见范围保持一致）：
 * <ul>
 *   <li>PUBLIC：任何登录用户可下载；</li>
 *   <li>DEPT：同部门可下载（owner_dept_id 为空时退化为"仅上传人"，不放大权限）；</li>
 *   <li>PRIVATE：仅上传人本人；</li>
 *   <li>持有 file:admin 的系统管理员不受上述限制。</li>
 * </ul>
 * 注意：权限码 file:download 只表示"允许调用下载接口"，真正能不能下载由上面这套判定决定。
 * 只靠权限码会退化成"有下载权限的人能下载全库文件"。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private static final String ADMIN_PERMISSION = "file:admin";

    private final FileMapper fileMapper;
    private final FileSupportMapper supportMapper;
    private final LocalFileStore fileStore;

    /** 单文件上传上限，与 Spring 的 multipart 上限保持一致口径 */
    @Value("${lz.file.max-size-bytes:20971520}")
    private long maxSizeBytes;

    /**
     * 通用上传。
     *
     * @param visibility PUBLIC / DEPT / PRIVATE；DEPT 必须带 ownerDeptId
     */
    public SysFile upload(MultipartFile file, String visibility, Long ownerDeptId, AuthUser user) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "上传文件为空");
        }
        if (file.getSize() > maxSizeBytes) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "文件超过上限 " + (maxSizeBytes / 1024 / 1024) + " MB");
        }
        String normalized = normalizeVisibility(visibility, ownerDeptId);
        String originalName = file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename();

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "读取上传文件失败");
        }

        LocalFileStore.StoredFile stored = fileStore.save(bytes, originalName, "common");
        SysFile record = new SysFile();
        record.setOriginalName(originalName);
        record.setStoragePath(stored.relativePath());
        record.setSize((long) bytes.length);
        record.setMime(file.getContentType());
        record.setUploaderId(user == null ? null : user.getUserId());
        record.setVisibility(normalized);
        record.setOwnerDeptId(SysFile.VISIBILITY_DEPT.equals(normalized) ? ownerDeptId : null);
        fileMapper.insert(record);
        log.info("[FILE] 上传文件 id={} name={} visibility={} size={}",
                record.getId(), originalName, normalized, bytes.length);
        return record;
    }

    /**
     * 鉴权下载：返回可读取的绝对路径与原始文件名。
     *
     * <p>只接受 id，不接受任何路径参数——防止调用方用 "../" 读到存储目录之外。</p>
     */
    public Downloadable download(Long id, AuthUser user, String clientIp) {
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        SysFile record = fileMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文件不存在或已被删除");
        }
        if (!canDownload(record, user)) {
            // 留痕但不回显文件是否存在：避免用错误码探测他人文件
            log.warn("[FILE] 越权下载被拒绝 fileId={} userId={}", id, user.getUserId());
            supportMapper.insertOperationLog(user.getUserId(), "file", "download",
                    String.valueOf(id), clientIp, 0, "越权下载被拒绝");
            throw new BusinessException(ResultCode.FORBIDDEN, "没有该文件的下载权限");
        }
        Path path = fileStore.resolve(record.getStoragePath());
        if (!Files.exists(path)) {
            // 库里有记录但磁盘上没有：属于数据不一致，必须明确报错而不是返回空流
            log.error("[FILE] 文件记录存在但磁盘缺失 fileId={} path={}", id, record.getStoragePath());
            throw new BusinessException(ResultCode.NOT_FOUND, "文件已丢失，请联系管理员");
        }
        supportMapper.insertOperationLog(user.getUserId(), "file", "download",
                String.valueOf(id), clientIp, 1, record.getOriginalName());
        return new Downloadable(path, safeFileName(record.getOriginalName()), record.getMime(), record.getSize());
    }

    /** 可见性判定，单独暴露给列表接口做 canDownload 标记 */
    public boolean canDownload(SysFile record, AuthUser user) {
        if (record == null || user == null) {
            return false;
        }
        if (user.hasPermission(ADMIN_PERMISSION)) {
            return true;
        }
        String visibility = record.getVisibility() == null
                ? SysFile.VISIBILITY_PRIVATE : record.getVisibility();
        switch (visibility) {
            case SysFile.VISIBILITY_PUBLIC:
                return true;
            case SysFile.VISIBILITY_DEPT: {
                Long fileDept = record.getOwnerDeptId();
                // 没有部门归属的 DEPT 文件不向全员开放，退化为"仅上传人"
                if (fileDept == null) {
                    return Objects.equals(record.getUploaderId(), user.getUserId());
                }
                Long userDept = user.getEmployeeId() == null
                        ? null : supportMapper.selectEmployeeDeptId(user.getEmployeeId());
                return fileDept.equals(userDept);
            }
            default:
                return Objects.equals(record.getUploaderId(), user.getUserId());
        }
    }

    /** 列表：管理员看全部，其余只看自己上传的（不展示无权下载的文件路径） */
    public List<SysFile> list(AuthUser user) {
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        List<SysFile> files = user.hasPermission(ADMIN_PERMISSION)
                ? fileMapper.selectAll()
                : fileMapper.selectByUploader(user.getUserId());
        // storagePath 绝不下发：脱敏后再返回
        files.forEach(f -> f.setStoragePath(null));
        return files;
    }

    public void delete(Long id, AuthUser user, String clientIp) {
        SysFile record = fileMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文件不存在");
        }
        boolean owner = Objects.equals(record.getUploaderId(), user == null ? null : user.getUserId());
        if (!owner && (user == null || !user.hasPermission(ADMIN_PERMISSION))) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能删除自己上传的文件");
        }
        fileStore.delete(record.getStoragePath());
        fileMapper.deleteById(id);
        supportMapper.insertOperationLog(user == null ? null : user.getUserId(), "file", "delete",
                String.valueOf(id), clientIp, 1, record.getOriginalName());
        log.info("[FILE] 删除文件 id={} name={}", id, record.getOriginalName());
    }

    private static String normalizeVisibility(String visibility, Long ownerDeptId) {
        if (visibility == null || visibility.isBlank()) {
            return SysFile.VISIBILITY_PRIVATE;
        }
        String v = visibility.trim().toUpperCase();
        if (SysFile.VISIBILITY_DEPT.equals(v) && ownerDeptId == null) {
            // DEPT 却没有部门，等于谁都能下：直接拒绝，不静默降级
            throw new BusinessException(ResultCode.PARAM_ERROR, "选择部门可见时必须指定部门");
        }
        return switch (v) {
            case "PUBLIC", "DEPT", "PRIVATE" -> v;
            default -> throw new BusinessException(ResultCode.PARAM_ERROR, "可见范围只能是 PUBLIC/DEPT/PRIVATE");
        };
    }

    /**
     * 文件名清洗：Content-Disposition 是响应头，原始文件名里的换行、引号会被
     * 当成头注入或破坏解析，这里统一剔除后只保留尾部真实文件名。
     */
    static String safeFileName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "download";
        }
        String name = originalName.replace("\\", "/");
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\r\\n\"']", "").trim();
        return name.isEmpty() ? "download" : name;
    }

    public record Downloadable(Path path, String fileName, String mime, Long size) {
    }
}
