package com.lianzhi.attendance.file.controller;

import com.lianzhi.attendance.common.result.R;
import com.lianzhi.attendance.file.entity.SysFile;
import com.lianzhi.attendance.file.service.FileService;
import com.lianzhi.attendance.framework.auth.AuthContext;
import com.lianzhi.attendance.framework.auth.AuthUser;
import com.lianzhi.attendance.framework.auth.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件接口：下载走鉴权，不暴露真实存储路径。
 *
 * <p>下载接口刻意不返回统一的 R 包装——它返回的是文件流本身，
 * 前端用 responseType: 'blob' 接收；鉴权失败时抛出的 BusinessException
 * 仍由 GlobalExceptionHandler 转成统一的 JSON，前后端分支不受影响。</p>
 */
@Tag(name = "文件", description = "受控存储与鉴权下载")
@RestController
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @Operation(summary = "上传文件", description = "可见范围 PUBLIC/DEPT/PRIVATE；DEPT 必须带 ownerDeptId")
    @PostMapping("/api/files")
    @RequirePermission("file:upload")
    public R<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                         @RequestParam(value = "visibility", required = false) String visibility,
                                         @RequestParam(value = "ownerDeptId", required = false) Long ownerDeptId) {
        SysFile record = fileService.upload(file, visibility, ownerDeptId, AuthContext.get());
        Map<String, Object> data = new HashMap<>();
        data.put("file", record);
        return R.ok(data);
    }

    @Operation(summary = "文件列表", description = "管理员看全部，其余只看自己上传的；不下发存储路径")
    @GetMapping("/api/files")
    @RequirePermission("file:download")
    public R<Map<String, Object>> list() {
        AuthUser user = AuthContext.get();
        List<SysFile> files = fileService.list(user);
        Map<String, Object> data = new HashMap<>();
        data.put("files", files);
        data.put("total", files.size());
        return R.ok(data);
    }

    @Operation(summary = "鉴权下载", description = "按可见范围判定：PUBLIC 全员、DEPT 同部门、PRIVATE 仅上传人")
    @GetMapping("/api/files/{id}/download")
    @RequirePermission("file:download")
    public ResponseEntity<Resource> download(@PathVariable("id") Long id,
                                             HttpServletRequest request) {
        FileService.Downloadable d = fileService.download(id, AuthContext.get(), clientIp(request));
        Resource resource = new FileSystemResource(d.path());
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (d.mime() != null && !d.mime().isBlank()) {
            try {
                mediaType = MediaType.parseMediaType(d.mime());
            } catch (Exception ignored) {
                // 上传时带的 MIME 不可信或格式非法：退回二进制流，不让请求因为解析失败而 500
            }
        }
        String encoded = URLEncoder.encode(d.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        // filename 必须是纯 ASCII：HTTP 头按 ISO-8859-1 解析，塞中文进去会被容器
        // 直接丢掉整个 Content-Disposition，表现是"下载下来文件名变成接口名"。
        // 中文名交给 filename*（RFC 5987），两者并存，老客户端退回 ASCII 名。
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + asciiFallback(d.fileName()) + "\"; filename*=UTF-8''" + encoded)
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(d.size() == null ? 0 : d.size()))
                .body(resource);
    }

    @Operation(summary = "删除文件", description = "仅上传人本人或持有 file:admin 的管理员")
    @DeleteMapping("/api/files/{id}")
    @RequirePermission("file:delete")
    public R<Void> delete(@PathVariable("id") Long id, HttpServletRequest request) {
        fileService.delete(id, AuthContext.get(), clientIp(request));
        return R.ok();
    }

    /** 把非 ASCII 字符换成下划线，保证 filename 在 ISO-8859-1 下合法 */
    private static String asciiFallback(String name) {
        StringBuilder sb = new StringBuilder();
        for (char c : name.toCharArray()) {
            sb.append(c > 127 ? '_' : c);
        }
        String result = sb.toString().trim();
        return result.isEmpty() ? "download" : result;
    }

    /**
     * 取客户端 IP 用于操作留痕。
     *
     * <p>容器/Nginx 部署时直接取 remoteAddr 会拿到网关地址，因此先看 X-Forwarded-For。
     * 该头可被伪造，所以只用于日志留痕，绝不用于鉴权判定。</p>
     */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
