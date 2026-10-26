package com.lianzhi.attendance.face.controller;

import com.lianzhi.attendance.common.page.PageResult;
import com.lianzhi.attendance.common.result.R;
import com.lianzhi.attendance.face.dto.FaceEnrollResult;
import com.lianzhi.attendance.face.dto.FaceStatusResult;
import com.lianzhi.attendance.face.dto.FaceVerifyResult;
import com.lianzhi.attendance.face.entity.FaceFeature;
import com.lianzhi.attendance.face.service.FaceService;
import com.lianzhi.attendance.framework.auth.AuthContext;
import com.lianzhi.attendance.framework.auth.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 人脸识别接口（模块 G）。
 *
 * <p>每个响应的 {@code mock} 字段都如实反映当前是否为模拟实现；
 * 界面必须展示该标记，不允许出现"看起来像真的人脸打卡"的呈现。</p>
 */
@Tag(name = "人脸识别（适配层）", description = "录入、校验与能力状态；默认使用明确标注的模拟实现")
@RestController
@RequiredArgsConstructor
public class FaceController {

    private final FaceService faceService;

    @Operation(summary = "录入人脸", description = "默认录入本人；管理员可指定 employeeId。响应含 mock 标记")
    @PostMapping("/api/face/enroll")
    @RequirePermission("face:enroll")
    public R<FaceEnrollResult> enroll(@RequestParam("image") MultipartFile image,
                                      @RequestParam(value = "employeeId", required = false) Long employeeId) {
        return R.ok(faceService.enroll(employeeId, bytesOf(image), AuthContext.get()));
    }

    @Operation(summary = "人脸校验", description = "与已录入特征比对；模拟实现下同一张图必定通过，仅作流程演示")
    @PostMapping("/api/face/verify")
    @RequirePermission("face:verify")
    public R<FaceVerifyResult> verify(@RequestParam("image") MultipartFile image,
                                      @RequestParam(value = "employeeId", required = false) Long employeeId) {
        return R.ok(faceService.verify(employeeId, bytesOf(image), AuthContext.get()));
    }

    @Operation(summary = "人脸识别能力状态", description = "返回 provider / 是否模拟 / 是否可用 / 隐私说明")
    @GetMapping("/api/face/status")
    @RequirePermission("face:verify")
    public R<FaceStatusResult> status() {
        return R.ok(faceService.status(AuthContext.get()));
    }

    @Operation(summary = "人脸录入情况", description = "仅系统管理员可见")
    @GetMapping("/api/face/features")
    @RequirePermission("face:enroll")
    public R<PageResult<FaceFeature>> features(@RequestParam(value = "keyword", required = false) String keyword,
                                               @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
                                               @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        return R.ok(faceService.list(keyword, pageNum, pageSize, AuthContext.get()));
    }

    private static byte[] bytesOf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return new byte[0];
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            return new byte[0];
        }
    }
}
