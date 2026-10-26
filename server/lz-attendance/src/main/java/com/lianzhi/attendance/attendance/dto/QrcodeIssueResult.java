package com.lianzhi.attendance.attendance.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 二维码签发结果。
 *
 * <p><code>payload</code> 会同时返回给前端：摄像头不可用时用户可以把这串内容
 * 手动粘贴到 H5 打卡页完成打卡（REQ-C-12 的兜底方案），
 * 这比"演示时因为没摄像头就走不下去"要可靠。</p>
 */
@Data
@Builder
public class QrcodeIssueResult {

    private Long id;
    private Long placeId;
    private String placeName;
    private String bizId;
    private String payload;
    private String qrType;
    private Integer onceOnly;
    private LocalDateTime issueTime;
    private LocalDateTime expireTime;
    private Long ttlSeconds;

    /** 图片地址（前端用 <img src> 直接渲染 PNG 流） */
    private String imageUrl;
}
