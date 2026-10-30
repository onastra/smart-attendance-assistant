package com.lianzhi.attendance.attendance.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.lianzhi.attendance.attendance.config.AttendanceProperties;
import com.lianzhi.attendance.attendance.dto.QrcodeIssueRequest;
import com.lianzhi.attendance.attendance.dto.QrcodeIssueResult;
import com.lianzhi.attendance.attendance.entity.AttQrcode;
import com.lianzhi.attendance.attendance.entity.AttRecord;
import com.lianzhi.attendance.attendance.mapper.AttQrcodeMapper;
import com.lianzhi.attendance.attendance.query.QrcodeQuery;
import com.lianzhi.attendance.attendance.sign.QrPayload;
import com.lianzhi.attendance.attendance.sign.QrSigner;
import com.lianzhi.attendance.common.exception.BusinessException;
import com.lianzhi.attendance.common.page.PageResult;
import com.lianzhi.attendance.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Map;

/**
 * 考勤二维码签发。
 *
 * <p>一次签发 = 生成一个随机 nonce + 计算过期时间 + 用服务端密钥对参数签名 + 落库 + 渲染 PNG。
 * 任何人拿到二维码图片都能读出其中的明文参数，但改任何一个字符，
 * 打卡时的签名校验都会失败——这就是"二维码动态生成并携带核心参数"的实质。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QrCodeService {

    private static final SecureRandom RANDOM = new SecureRandom();
    /** 去掉 I/O/0/1 等易混淆字符，便于人工抄写比对 */
    private static final String NONCE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int NONCE_LENGTH = 16;

    private final AttQrcodeMapper qrcodeMapper;
    private final QrSigner qrSigner;
    private final AttendanceProperties properties;
    private final OperationLogService operationLogService;

    public PageResult<AttQrcode> page(QrcodeQuery query) {
        // 惰性过期：先落库状态再统计，避免列表里出现"状态 VALID 但已超时"的矛盾展示
        qrcodeMapper.markExpired(LocalDateTime.now());
        long total = qrcodeMapper.countByQuery(query);
        if (total == 0) {
            return PageResult.empty(query);
        }
        return PageResult.of(query, total, qrcodeMapper.selectByQuery(query));
    }

    public AttQrcode detail(Long id) {
        AttQrcode qrcode = qrcodeMapper.selectById(id);
        if (qrcode == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "二维码不存在");
        }
        return qrcode;
    }

    /**
     * 签发二维码。
     *
     * @param operatorId 签发人（用于留痕与列表展示）
     */
    @Transactional
    public QrcodeIssueResult issue(QrcodeIssueRequest request, Long operatorId, String clientIp) {
        Long placeId = request.getPlaceId();
        if (placeId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "必须指定场所");
        }
        if (qrcodeMapper.countUsablePlace(placeId) == 0) {
            throw new BusinessException(ResultCode.CONFLICT, "场所不存在或已停用，不能签发二维码");
        }

        String qrType = normalizeType(request.getQrType());
        String checkinType = normalizeCheckinType(request.getCheckinType());
        int ttlSeconds = resolveTtl(request.getTtlSeconds(), qrType);
        int onceOnly = request.getOnceOnly() == null ? 0 : (request.getOnceOnly() == 1 ? 1 : 0);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireTime = now.plusSeconds(ttlSeconds);
        String nonce = generateNonce();
        long expireEpoch = expireTime.atZone(ZoneId.systemDefault()).toEpochSecond();

        String canonical = QrPayload.canonical(placeId, nonce, expireEpoch);
        String sign = qrSigner.sign(canonical);
        String payload = canonical + "|s=" + sign;

        AttQrcode qrcode = new AttQrcode();
        qrcode.setPlaceId(placeId);
        qrcode.setBizId(nonce);
        qrcode.setPayload(payload);
        qrcode.setSign(sign);
        qrcode.setQrType(qrType);
        qrcode.setCheckinType(checkinType);
        qrcode.setOnceOnly(onceOnly);
        qrcode.setStatus(AttQrcode.STATUS_VALID);
        qrcode.setIssueTime(now);
        qrcode.setExpireTime(expireTime);
        qrcode.setCreatedBy(operatorId);
        qrcodeMapper.insert(qrcode);

        operationLogService.log(operatorId, "attendance", "QRCODE_ISSUE",
                String.valueOf(qrcode.getId()), clientIp, true,
                "签发二维码 placeId=" + placeId + " type=" + qrType + " checkinType=" + checkinType
                        + " ttl=" + ttlSeconds + "s");

        log.info("[QR] 签发二维码 id={} placeId={} type={} checkinType={} ttl={}s onceOnly={} operator={}",
                qrcode.getId(), placeId, qrType, checkinType, ttlSeconds, onceOnly, operatorId);

        AttQrcode saved = qrcodeMapper.selectById(qrcode.getId());
        return QrcodeIssueResult.builder()
                .id(saved.getId())
                .placeId(placeId)
                .placeName(saved.getPlaceName())
                .bizId(nonce)
                .payload(payload)
                .qrType(qrType)
                .checkinType(checkinType)
                .onceOnly(onceOnly)
                .issueTime(now)
                .expireTime(expireTime)
                .ttlSeconds((long) ttlSeconds)
                .imageUrl("/api/qrcodes/" + saved.getId() + "/image")
                .build();
    }

    /**
     * 渲染 PNG。
     *
     * <p>图片内容就是 {@code payload}，不做任何装饰：
     * 一旦在图上叠加说明文字，扫码得到的字符串就可能混入无关内容。</p>
     */
    public byte[] renderPng(Long id) {
        AttQrcode qrcode = detail(id);
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = new MultiFormatWriter().encode(
                    qrcode.getPayload(), BarcodeFormat.QR_CODE,
                    properties.getQrImageSize(), properties.getQrImageSize(), hints);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("[QR] 渲染二维码失败 id={}", id, e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "二维码图片生成失败");
        }
    }

    /**
     * 批量标记过期：把"状态还是 VALID 但已超过 expire_time 的码"统一置为 EXPIRED。
     *
     * <p>只改状态、不物理删除：二维码会被打卡记录引用，删掉后历史打卡就查不到码的来源；
     * 过期判定以 expire_time 为准，重复执行不会重复计数（已 EXPIRED 的行不会再被命中）。</p>
     *
     * @return 本次被置为过期的行数
     */
    public int expireOutdated() {
        int rows = qrcodeMapper.markExpired(LocalDateTime.now());
        if (rows > 0) {
            log.info("[QR] 过期二维码清理：本次置为 EXPIRED {} 条", rows);
        }
        return rows;
    }

    /** 手动失效：管理员发现二维码外泄时可立即作废 */
    @Transactional
    public void invalidate(Long id, Long operatorId, String clientIp) {
        AttQrcode qrcode = detail(id);
        if (AttQrcode.STATUS_INVALID.equals(qrcode.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "该二维码已经是失效状态");
        }
        qrcodeMapper.updateStatus(id, AttQrcode.STATUS_INVALID);
        operationLogService.log(operatorId, "attendance", "QRCODE_INVALIDATE",
                String.valueOf(id), clientIp, true, "手动失效二维码 placeId=" + qrcode.getPlaceId());
        log.info("[QR] 手动失效二维码 id={} operator={}", id, operatorId);
    }

    private String normalizeType(String qrType) {
        if (qrType == null || qrType.isBlank()) {
            return AttQrcode.TYPE_DYNAMIC;
        }
        String normalized = qrType.trim().toUpperCase();
        if (AttQrcode.TYPE_FIXED.equals(normalized)) {
            return AttQrcode.TYPE_FIXED;
        }
        if (AttQrcode.TYPE_DYNAMIC.equals(normalized)) {
            return AttQrcode.TYPE_DYNAMIC;
        }
        throw new BusinessException(ResultCode.PARAM_ERROR, "二维码类型只能是 DYNAMIC 或 FIXED");
    }

    /** 打卡类型绑定值归一化：默认 AUTO（按打卡时间自动判定），兼容补丁前未带该字段的调用方 */
    private String normalizeCheckinType(String checkinType) {
        if (checkinType == null || checkinType.isBlank()) {
            return AttRecord.TYPE_AUTO;
        }
        String normalized = checkinType.trim().toUpperCase();
        if (AttRecord.TYPE_ON_DUTY.equals(normalized)
                || AttRecord.TYPE_OFF_DUTY.equals(normalized)
                || AttRecord.TYPE_FIELD.equals(normalized)) {
            return normalized;
        }
        if (AttRecord.TYPE_AUTO.equals(normalized)) {
            return AttRecord.TYPE_AUTO;
        }
        throw new BusinessException(ResultCode.PARAM_ERROR,
                "打卡类型只能是 AUTO / ON_DUTY / OFF_DUTY / FIELD");
    }

    private int resolveTtl(Integer requested, String qrType) {
        int fallback = AttQrcode.TYPE_FIXED.equals(qrType)
                ? properties.getQrFixedTtlSeconds()
                : properties.getQrDynamicTtlSeconds();
        if (requested == null) {
            return fallback;
        }
        if (requested <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "有效期必须为正数");
        }
        // 设上限是为了避免签发出"永不过期"的二维码，那会让动态码机制失去意义
        if (requested > properties.getQrMaxTtlSeconds()) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "有效期不能超过 " + properties.getQrMaxTtlSeconds() + " 秒");
        }
        return requested;
    }

    private String generateNonce() {
        StringBuilder sb = new StringBuilder(NONCE_LENGTH);
        for (int i = 0; i < NONCE_LENGTH; i++) {
            sb.append(NONCE_CHARS.charAt(RANDOM.nextInt(NONCE_CHARS.length())));
        }
        String candidate = sb.toString();
        if (qrcodeMapper.selectByBizId(candidate) != null) {
            return generateNonce();
        }
        return candidate;
    }
}
