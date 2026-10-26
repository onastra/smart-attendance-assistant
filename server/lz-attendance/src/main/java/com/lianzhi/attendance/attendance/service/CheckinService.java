package com.lianzhi.attendance.attendance.service;

import com.lianzhi.attendance.attendance.config.AttendanceProperties;
import com.lianzhi.attendance.attendance.dto.CheckinRequest;
import com.lianzhi.attendance.attendance.dto.CheckinResult;
import com.lianzhi.attendance.attendance.entity.AttQrcode;
import com.lianzhi.attendance.attendance.entity.AttRecord;
import com.lianzhi.attendance.attendance.entity.AttRecordView;
import com.lianzhi.attendance.attendance.entity.PlaceBrief;
import com.lianzhi.attendance.attendance.mapper.AttQrcodeMapper;
import com.lianzhi.attendance.attendance.mapper.AttRecordMapper;
import com.lianzhi.attendance.attendance.mapper.AttSupportMapper;
import com.lianzhi.attendance.attendance.sign.QrPayload;
import com.lianzhi.attendance.attendance.sign.QrSigner;
import com.lianzhi.attendance.attendance.util.GeoDistanceUtil;
import com.lianzhi.attendance.common.exception.BusinessException;
import com.lianzhi.attendance.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 打卡核心逻辑。
 *
 * <p>校验顺序不是随意排的：先验证二维码本身的真伪与时效，再验证人的身份与场所归属，
 * 最后才做业务去重与围栏。这样返回的失败原因对使用者才是有意义的——
 * "码是假的"和"你不属于这个场所"是两件完全不同的事，不能混成一句"打卡失败"。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckinService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final AttQrcodeMapper qrcodeMapper;
    private final AttRecordMapper recordMapper;
    private final AttSupportMapper supportMapper;
    private final QrSigner qrSigner;
    private final AttendanceProperties properties;
    private final OperationLogService operationLogService;

    /**
     * 签发幂等请求号。
     *
     * <p>H5 页面每次打开打卡页时取一个，提交时带回；网络重试、用户双击都会命中同一条记录，
     * 不会因为一次操作产生两条打卡流水。</p>
     */
    public String issueRequestId(Long userId) {
        String requestId = "CK" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        log.debug("[CHECKIN] 签发 requestId userId={}", userId);
        return requestId;
    }

    @Transactional
    public CheckinResult checkin(CheckinRequest request, Long userId, Long employeeId, String clientIp) {
        // ---------- 1. 二维码真伪 ----------
        QrPayload payload;
        try {
            payload = QrPayload.parse(request.getContent());
        } catch (IllegalArgumentException e) {
            fail(userId, employeeId, clientIp, "CHECKIN", request.getRequestId(), e.getMessage());
            throw new BusinessException(ResultCode.PARAM_ERROR, e.getMessage(), reason("QR_FORMAT_INVALID"));
        }

        AttQrcode qrcode = qrcodeMapper.selectByBizId(payload.getBizId());
        if (qrcode == null) {
            fail(userId, employeeId, clientIp, "CHECKIN", request.getRequestId(), "二维码不存在");
            throw new BusinessException(ResultCode.NOT_FOUND, "二维码不是本系统签发的", reason("QR_NOT_FOUND"));
        }
        if (!qrSigner.matches(payload.canonical(), payload.getSign())) {
            fail(userId, employeeId, clientIp, "CHECKIN", request.getRequestId(), "签名校验失败，疑似伪造二维码");
            throw new BusinessException(ResultCode.CONFLICT, "二维码校验失败，可能已被篡改", reason("QR_SIGN_INVALID"));
        }
        // 二维码中的场所可能被替换：以签名覆盖范围内的参数为准，但仍要确认与库内记录一致
        if (!qrcode.getPlaceId().equals(payload.getPlaceId())) {
            fail(userId, employeeId, clientIp, "CHECKIN", request.getRequestId(), "二维码场所与签发记录不一致");
            throw new BusinessException(ResultCode.CONFLICT, "二维码与签发记录不一致", reason("QR_PAYLOAD_MISMATCH"));
        }

        // ---------- 2. 时效与状态 ----------
        LocalDateTime now = LocalDateTime.now();
        String status = qrcode.getStatus();
        if (AttQrcode.STATUS_VALID.equals(status) && qrcode.getExpireTime().isBefore(now)) {
            // 惰性过期：把库里状态修正过来，列表页与统计就不会看到自相矛盾的数据
            qrcodeMapper.markExpired(now);
            status = AttQrcode.STATUS_EXPIRED;
        }
        if (AttQrcode.STATUS_EXPIRED.equals(status)) {
            fail(userId, employeeId, clientIp, "CHECKIN", request.getRequestId(), "二维码已过期");
            throw new BusinessException(ResultCode.CONFLICT, "该二维码已过期，请向管理员获取最新二维码",
                    reason("QR_EXPIRED"));
        }
        if (AttQrcode.STATUS_INVALID.equals(status)) {
            fail(userId, employeeId, clientIp, "CHECKIN", request.getRequestId(), "二维码已被管理员失效");
            throw new BusinessException(ResultCode.CONFLICT, "该二维码已被管理员作废", reason("QR_INVALID"));
        }
        if (AttQrcode.STATUS_USED.equals(status)) {
            fail(userId, employeeId, clientIp, "CHECKIN", request.getRequestId(), "一次性二维码已被使用");
            throw new BusinessException(ResultCode.CONFLICT, "这是一次性二维码且已被使用过", reason("QR_USED"));
        }

        // ---------- 3. 场所与人 ----------
        PlaceBrief place = supportMapper.selectPlaceBrief(qrcode.getPlaceId());
        if (place == null || place.getStatus() == null || place.getStatus() != 1) {
            fail(userId, employeeId, clientIp, "CHECKIN", request.getRequestId(), "场所不存在或已停用");
            throw new BusinessException(ResultCode.CONFLICT, "该办公场所已停用，无法打卡", reason("PLACE_DISABLED"));
        }
        if (employeeId == null) {
            fail(userId, employeeId, clientIp, "CHECKIN", request.getRequestId(), "账号未关联员工档案");
            throw new BusinessException(ResultCode.CONFLICT, "当前账号未关联员工档案，无法打卡",
                    reason("EMPLOYEE_NOT_BOUND"));
        }
        if (supportMapper.countActiveEmployee(employeeId) == 0) {
            fail(userId, employeeId, clientIp, "CHECKIN", request.getRequestId(), "员工不存在或已停用");
            throw new BusinessException(ResultCode.CONFLICT, "员工档案不存在或已停用，无法打卡",
                    reason("EMPLOYEE_INACTIVE"));
        }
        if (supportMapper.countBinding(employeeId, qrcode.getPlaceId()) == 0) {
            fail(userId, employeeId, clientIp, "CHECKIN", request.getRequestId(),
                    "员工未绑定该场所 employeeId=" + employeeId + " placeId=" + qrcode.getPlaceId());
            throw new BusinessException(ResultCode.CONFLICT, "你不属于该办公场所，无法在此打卡",
                    reason("PLACE_NOT_BOUND"));
        }

        // ---------- 4. 幂等：同一 requestId 直接返回首次结果 ----------
        if (recordMapper.countByRequestId(request.getRequestId()) > 0) {
            AttRecordView existing = recordMapper.selectByRequestId(request.getRequestId());
            if (existing == null || !employeeId.equals(existing.getEmployeeId())) {
                throw new BusinessException(ResultCode.CONFLICT, "幂等请求号已被使用，请刷新页面后重试",
                        reason("REQUEST_ID_CONFLICT"));
            }
            log.info("[CHECKIN] 命中幂等 requestId={} recordId={}", request.getRequestId(), existing.getId());
            return toResult(existing, true, null, place.getCoverRadiusM());
        }

        String checkinType = resolveCheckinType(request.getCheckinType(), now);

        // ---------- 5. 重复打卡窗口 ----------
        LocalDateTime windowStart = now.minusMinutes(properties.getDuplicateWindowMinutes());
        if (recordMapper.countRecent(employeeId, qrcode.getPlaceId(), checkinType, windowStart) > 0) {
            fail(userId, employeeId, clientIp, "CHECKIN", request.getRequestId(),
                    "窗口内重复打卡 " + properties.getDuplicateWindowMinutes() + " 分钟");
            throw new BusinessException(ResultCode.CONFLICT,
                    "你在 " + properties.getDuplicateWindowMinutes() + " 分钟内已经打过一次" + typeText(checkinType)
                            + "卡，无需重复打卡",
                    reason("DUPLICATE_CHECKIN"));
        }

        // ---------- 6. 围栏校验（可选）----------
        boolean locationChecked = false;
        BigDecimal distanceMeters = null;
        Integer radiusMeters = place.getCoverRadiusM() == null ? 200 : place.getCoverRadiusM();
        if (properties.isLocationCheckEnabled() && request.getLng() != null && request.getLat() != null) {
            Double distance = GeoDistanceUtil.distanceMeters(
                    place.getLng(), place.getLat(), request.getLng(), request.getLat());
            if (distance != null) {
                locationChecked = true;
                distanceMeters = BigDecimal.valueOf(distance).setScale(1, RoundingMode.HALF_UP);
                if (distance > radiusMeters) {
                    fail(userId, employeeId, clientIp, "CHECKIN", request.getRequestId(),
                            "超出围栏 distance=" + distanceMeters + "m radius=" + radiusMeters + "m");
                    throw new BusinessException(ResultCode.CONFLICT,
                            "你距该场所约 " + distanceMeters + " 米，超出允许的 " + radiusMeters + " 米范围",
                            reason("OUT_OF_RANGE", Map.of(
                                    "distanceMeters", distanceMeters,
                                    "radiusMeters", radiusMeters)));
                }
            }
        }

        // ---------- 7. 判定结果并落库 ----------
        String result = resolveResult(checkinType, now);
        String remark = request.getRemark();
        if (!locationChecked) {
            String note = "客户端未上报坐标，本次未做围栏校验";
            remark = (remark == null || remark.isBlank()) ? note : remark + "；" + note;
        }

        AttRecord record = new AttRecord();
        record.setEmployeeId(employeeId);
        record.setPlaceId(qrcode.getPlaceId());
        record.setQrcodeId(qrcode.getId());
        record.setCheckinType(checkinType);
        record.setCheckinTime(now);
        record.setSource(AttRecord.SOURCE_H5);
        record.setResult(result);
        record.setLng(request.getLng());
        record.setLat(request.getLat());
        record.setClientIp(clientIp);
        record.setRequestId(request.getRequestId());
        record.setRemark(remark);
        recordMapper.insert(record);

        // 一次性码：用完立即置为 USED（并发下由数据库状态条件保证只有一个请求成功）
        if (qrcode.getOnceOnly() != null && qrcode.getOnceOnly() == 1) {
            qrcodeMapper.markUsed(qrcode.getId(), now);
        }

        operationLogService.log(userId, "attendance", "CHECKIN", String.valueOf(record.getId()),
                clientIp, true, "打卡成功 placeId=" + qrcode.getPlaceId() + " type=" + checkinType
                        + " result=" + result + " locationChecked=" + locationChecked);
        log.info("[CHECKIN] 打卡成功 recordId={} employeeId={} placeId={} type={} result={}",
                record.getId(), employeeId, qrcode.getPlaceId(), checkinType, result);

        AttRecordView saved = recordMapper.selectByRequestId(request.getRequestId());
        return toResult(saved, false, distanceMeters, radiusMeters);
    }

    /** 判定打卡类型：未显式指定时按当前时间自动判定 */
    private String resolveCheckinType(String requested, LocalDateTime now) {
        if (requested != null && !requested.isBlank()) {
            String normalized = requested.trim().toUpperCase();
            if (AttRecord.TYPE_ON_DUTY.equals(normalized)
                    || AttRecord.TYPE_OFF_DUTY.equals(normalized)
                    || AttRecord.TYPE_FIELD.equals(normalized)) {
                return normalized;
            }
            throw new BusinessException(ResultCode.PARAM_ERROR, "打卡类型只能是 ON_DUTY / OFF_DUTY / FIELD");
        }
        return now.toLocalTime().isBefore(LocalTime.NOON) ? AttRecord.TYPE_ON_DUTY : AttRecord.TYPE_OFF_DUTY;
    }

    /**
     * 迟到/早退判定（2026-10-26 确认的全局口径）。
     *
     * <p>规则集中在 <code>lz.attendance.*</code> 配置，不写死在代码里：
     * 上班晚于「上班时间 + 宽限分钟」记 LATE，下班早于「下班时间 - 宽限分钟」记 EARLY，外勤不判异常。</p>
     */
    private String resolveResult(String checkinType, LocalDateTime checkinTime) {
        if (AttRecord.TYPE_FIELD.equals(checkinType)) {
            return AttRecord.RESULT_NORMAL;
        }
        LocalTime current = checkinTime.toLocalTime();
        LocalTime workStart = LocalTime.parse(properties.getWorkStartTime(), TIME_FORMATTER);
        LocalTime workEnd = LocalTime.parse(properties.getWorkEndTime(), TIME_FORMATTER);
        if (AttRecord.TYPE_ON_DUTY.equals(checkinType)) {
            return current.isAfter(workStart.plusMinutes(properties.getLateGraceMinutes()))
                    ? AttRecord.RESULT_LATE
                    : AttRecord.RESULT_NORMAL;
        }
        return current.isBefore(workEnd.minusMinutes(properties.getEarlyLeaveGraceMinutes()))
                ? AttRecord.RESULT_EARLY
                : AttRecord.RESULT_NORMAL;
    }

    private CheckinResult toResult(AttRecordView view, boolean idempotent,
                                   BigDecimal distanceMeters, Integer radiusMeters) {
        return CheckinResult.builder()
                .recordId(view.getId())
                .employeeId(view.getEmployeeId())
                .empNo(view.getEmpNo())
                .employeeName(view.getEmployeeName())
                .placeName(view.getPlaceName())
                .checkinType(view.getCheckinType())
                .checkinTypeText(view.getCheckinTypeText())
                .checkinTime(view.getCheckinTime())
                .result(view.getResult())
                .resultText(view.getResultText())
                .source(view.getSource())
                .remark(view.getRemark())
                .idempotent(idempotent)
                .locationChecked(distanceMeters != null)
                .distanceMeters(distanceMeters)
                .radiusMeters(radiusMeters)
                .build();
    }

    private void fail(Long userId, Long employeeId, String clientIp, String action,
                      String targetId, String message) {
        operationLogService.log(userId, "attendance", "CHECKIN_FAIL", targetId, clientIp, false,
                "employeeId=" + employeeId + " " + message);
    }

    private static Map<String, Object> reason(String code) {
        return Map.of("reason", code);
    }

    private static Map<String, Object> reason(String code, Map<String, Object> extra) {
        Map<String, Object> merged = new HashMap<>(extra);
        merged.put("reason", code);
        return merged;
    }

    private static String typeText(String checkinType) {
        return switch (checkinType) {
            case AttRecord.TYPE_ON_DUTY -> "上班";
            case AttRecord.TYPE_OFF_DUTY -> "下班";
            default -> "外勤";
        };
    }

}
