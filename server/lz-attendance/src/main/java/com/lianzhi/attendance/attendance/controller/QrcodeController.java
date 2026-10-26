package com.lianzhi.attendance.attendance.controller;

import com.lianzhi.attendance.attendance.dto.QrcodeIssueRequest;
import com.lianzhi.attendance.attendance.dto.QrcodeIssueResult;
import com.lianzhi.attendance.attendance.entity.AttQrcode;
import com.lianzhi.attendance.attendance.query.QrcodeQuery;
import com.lianzhi.attendance.attendance.service.QrCodeService;
import com.lianzhi.attendance.attendance.util.WebUtil;
import com.lianzhi.attendance.common.page.PageResult;
import com.lianzhi.attendance.common.result.R;
import com.lianzhi.attendance.framework.auth.AuthContext;
import com.lianzhi.attendance.framework.auth.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 考勤二维码接口（管理员侧）。
 */
@Tag(name = "考勤二维码", description = "签发、查看、失效考勤二维码；签名由服务端生成并校验")
@Validated
@RestController
@RequestMapping("/api/qrcodes")
@RequiredArgsConstructor
public class QrcodeController {

    private final QrCodeService qrCodeService;

    @Operation(summary = "签发二维码", description = "返回 payload 原文，摄像头不可用时可手动粘贴到打卡页")
    @PostMapping
    @RequirePermission("attendance:qrcode:create")
    public R<QrcodeIssueResult> issue(@Valid @RequestBody QrcodeIssueRequest request,
                                      HttpServletRequest httpRequest) {
        Long operatorId = AuthContext.currentUserId();
        return R.ok(qrCodeService.issue(request, operatorId, WebUtil.clientIp(httpRequest)));
    }

    /**
     * 二维码图片。
     *
     * <p>直接返回 PNG 字节流而不是 JSON：前端用 &lt;img src&gt; 就能渲染，
     * 省掉 base64 编解码，也避免把二进制塞进统一响应体。</p>
     */
    @Operation(summary = "二维码图片（PNG）")
    @GetMapping("/{id}/image")
    @RequirePermission("attendance:qrcode:list")
    public ResponseEntity<byte[]> image(@PathVariable Long id) {
        byte[] png = qrCodeService.renderPng(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.IMAGE_PNG)
                .body(png);
    }

    @Operation(summary = "二维码分页列表")
    @GetMapping
    @RequirePermission("attendance:qrcode:list")
    public R<PageResult<AttQrcode>> page(QrcodeQuery query) {
        return R.ok(qrCodeService.page(query));
    }

    @Operation(summary = "手动失效二维码")
    @PostMapping("/{id}/invalidate")
    @RequirePermission("attendance:qrcode:invalidate")
    public R<Void> invalidate(@PathVariable Long id, HttpServletRequest httpRequest) {
        qrCodeService.invalidate(id, AuthContext.currentUserId(), WebUtil.clientIp(httpRequest));
        return R.ok();
    }
}
