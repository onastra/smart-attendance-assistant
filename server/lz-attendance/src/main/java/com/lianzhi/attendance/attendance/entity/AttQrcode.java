package com.lianzhi.attendance.attendance.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 考勤二维码。
 *
 * <p>一次签发对应一行。<code>payload</code> 是二维码中被编码的原文，
 * <code>sign</code> 是服务端对该原文做的 HMAC-SHA256 签名——
 * 签名不进二维码图片内容之外的信道，任何人改一个字符都会导致校验失败，
 * 这是"防止二维码被截图转发后篡改参数"的关键。</p>
 */
@Data
public class AttQrcode {

    /** 状态：有效 */
    public static final String STATUS_VALID = "VALID";
    /** 状态：管理员手动失效 */
    public static final String STATUS_INVALID = "INVALID";
    /** 状态：超过有效期（由查询时惰性落库） */
    public static final String STATUS_EXPIRED = "EXPIRED";
    /** 状态：一次性码已被使用 */
    public static final String STATUS_USED = "USED";

    /** 二维码类型：动态码（短有效期，前端倒计时刷新） */
    public static final String TYPE_DYNAMIC = "DYNAMIC";
    /** 二维码类型：长期固定码（默认 24 小时，靠管理员手动失效） */
    public static final String TYPE_FIXED = "FIXED";

    private Long id;
    private Long placeId;

    /** 业务随机串 nonce：防重放，同时是二维码的业务主键 */
    private String bizId;

    /** 二维码原文（含 placeId/bizId/expireAt） */
    private String payload;

    /** HMAC-SHA256 签名（十六进制小写） */
    private String sign;

    private String qrType;

    /** 1 一次性（用后即 USED） 0 可重复使用至过期 */
    private Integer onceOnly;

    private String status;
    private LocalDateTime issueTime;
    private LocalDateTime expireTime;
    private LocalDateTime usedTime;
    private Long createdBy;
    private LocalDateTime createdAt;

    // ===== 展示用辅助字段（由 SQL 别名填充，不入库）=====
    private String placeName;
    /** 剩余有效秒数；已失效为 0 */
    private Long remainSeconds;
}
