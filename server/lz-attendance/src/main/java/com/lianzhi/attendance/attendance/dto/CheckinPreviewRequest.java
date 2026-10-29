package com.lianzhi.attendance.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 扫码预校验入参（不落库）。
 *
 * <p>只用于小程序"确认打卡"页展示场所信息，因此不接受 requestId、checkinType——
 * 预校验不做任何写入，也不判定迟到早退（那是提交时按真实时间算的）。</p>
 */
@Data
public class CheckinPreviewRequest {

    /** 二维码原文（扫码结果），也允许是手动粘贴的同一串内容 */
    @NotBlank(message = "请扫码或输入二维码内容")
    private String content;

    /** 客户端上报的经度；未授权定位时可以不传，此时不做围栏预判 */
    private BigDecimal lng;

    /** 客户端上报的纬度 */
    private BigDecimal lat;
}
