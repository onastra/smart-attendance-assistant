package com.lianzhi.attendance.app.web;

import com.lianzhi.attendance.attendance.dto.CheckinResult;
import com.lianzhi.attendance.attendance.dto.FaceCheckinRequest;
import com.lianzhi.attendance.attendance.service.CheckinService;
import com.lianzhi.attendance.attendance.util.WebUtil;
import com.lianzhi.attendance.common.exception.BusinessException;
import com.lianzhi.attendance.common.result.R;
import com.lianzhi.attendance.common.result.ResultCode;
import com.lianzhi.attendance.face.dto.FaceVerifyResult;
import com.lianzhi.attendance.face.service.FaceService;
import com.lianzhi.attendance.framework.auth.AuthContext;
import com.lianzhi.attendance.framework.auth.AuthUser;
import com.lianzhi.attendance.framework.auth.RequireLogin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 刷脸打卡入口（考勤通道 × 人脸识别 的编排层）。
 *
 * <p>放在 lz-app 而不是任一业务模块里：人脸核验属于 lz-face、打卡落库属于 lz-attendance，
 * 模块间不产生编译期依赖（见 docs/architecture.md），聚合编排放在应用层最合适。</p>
 *
 * <p>流程：人脸核验（活体检测 + 与本人已录特征 1:1 比对，目标身份一律取自 token）
 * → 核验通过后走 {@link CheckinService#faceCheckin} 完成场所校验、防重、围栏、落库。
 * 人脸核验不通过绝不落考勤流水，失败原因原样透传给小程序展示。</p>
 */
@Tag(name = "刷脸打卡", description = "人脸核验通过后写入考勤流水（source=FACE）")
@RestController
@RequiredArgsConstructor
public class FaceCheckinController {

    private final FaceService faceService;
    private final CheckinService checkinService;

    @Operation(summary = "刷脸打卡",
            description = "multipart 提交：image=现场照片，requestId=幂等号，placeId=本人绑定场所，"
                    + "checkinType=AUTO/ON_DUTY/OFF_DUTY（外勤需使用签发二维码），lng/lat 可选用于围栏校验")
    @PostMapping("/api/checkin/face")
    @RequireLogin
    public R<CheckinResult> faceCheckin(@RequestParam("image") MultipartFile image,
                                        @RequestParam("requestId") String requestId,
                                        @RequestParam("placeId") Long placeId,
                                        @RequestParam(value = "checkinType", required = false, defaultValue = "AUTO")
                                        String checkinType,
                                        @RequestParam(value = "lng", required = false) BigDecimal lng,
                                        @RequestParam(value = "lat", required = false) BigDecimal lat,
                                        HttpServletRequest httpRequest) {
        AuthUser user = AuthContext.get();

        // ---------- 1. 人脸核验（活体 + 1:1 比对，比对对象=token 对应本人） ----------
        FaceVerifyResult vr = faceService.verify(null, bytesOf(image), user);
        if (!vr.isPassed()) {
            if ("NOT_ENROLLED".equals(vr.getReason())) {
                throw new BusinessException(ResultCode.CONFLICT, "你还没有录入人脸，请先完成人脸注册",
                        Map.of("reason", "NOT_ENROLLED"));
            }
            throw new BusinessException(ResultCode.CONFLICT,
                    "人脸核验未通过（相似度 " + String.format("%.4f", vr.getSimilarity())
                            + "，低于阈值 " + String.format("%.2f", vr.getThreshold()) + "），请正对镜头重拍",
                    Map.of("reason", "FACE_MISMATCH"));
        }

        // ---------- 2. 核验通过，进入与扫码同一口径的打卡流程 ----------
        FaceCheckinRequest request = new FaceCheckinRequest();
        request.setRequestId(requestId);
        request.setPlaceId(placeId);
        request.setCheckinType(checkinType);
        request.setLng(lng);
        request.setLat(lat);
        return R.ok(checkinService.faceCheckin(request, user.getUserId(), user.getEmployeeId(),
                WebUtil.clientIp(httpRequest), vr.getSimilarity()));
    }

    private static byte[] bytesOf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请先拍摄人脸照片");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "照片读取失败，请重拍");
        }
    }
}
