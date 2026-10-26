package com.lianzhi.attendance.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 打卡入参。
 */
@Data
public class CheckinRequest {

    /** 二维码原文（扫码结果），也允许是用户手动粘贴的同一串内容 */
    @NotBlank(message = "请扫码或输入二维码内容")
    private String content;

    /** 幂等请求号：进入打卡页时向服务端申请一次，重复提交不会产生第二条记录 */
    @NotBlank(message = "缺少幂等请求号，请刷新打卡页面")
    private String requestId;

    /** ON_DUTY / OFF_DUTY / FIELD；不传时由服务端按当前时间自动判定 */
    private String checkinType;

    /** 客户端上报的经度；浏览器未授权定位时可以不传 */
    private BigDecimal lng;

    /** 客户端上报的纬度；浏览器未授权定位时可以不传 */
    private BigDecimal lat;

    /** 备注，可为空 */
    private String remark;
}
