package com.lianzhi.attendance.attendance.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 二维码签发入参。
 */
@Data
public class QrcodeIssueRequest {

    @NotNull(message = "必须指定场所")
    private Long placeId;

    /** DYNAMIC 动态码（默认）/ FIXED 长期固定码 */
    private String qrType;

    /** 有效期（秒）；不传时按类型取默认值，上限由 lz.attendance.qr-max-ttl-seconds 控制 */
    private Integer ttlSeconds;

    /** 是否一次性：1 用后即失效 0 有效期内可重复使用（默认） */
    private Integer onceOnly;

    /**
     * 该码绑定的打卡类型：AUTO（默认，按打卡时间自动判定）/ON_DUTY/OFF_DUTY/FIELD。
     * 非 AUTO 时，扫此码打卡一律记为该类型。
     */
    private String checkinType;
}
