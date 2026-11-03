package com.lianzhi.attendance.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 人脸打卡请求（刷脸通道）。
 *
 * <p>与扫码通道的关键差异：二维码同时承载「场所 + 打卡类型 + 时间窗口」，
 * 而人脸通道没有码，因此由员工在页面上声明打卡意图：</p>
 * <ul>
 *   <li>placeId：员工从自己的已绑定场所中选择（后端复验绑定关系与围栏）；</li>
 *   <li>checkinType：上班 / 下班 / 外勤 / 自动判定（AUTO 按全局上下班配置推断）；</li>
 *   <li>身份不在此传——一律取自 token，照片只用于与本人已录特征 1:1 比对。</li>
 * </ul>
 */
@Data
public class FaceCheckinRequest {

    /** 幂等请求号（/api/checkin/request-id 获取），网络重试不产生第二条记录 */
    @NotBlank(message = "缺少幂等请求号")
    private String requestId;

    /** 打卡场所：必须是本人已绑定的启用场所 */
    @NotNull(message = "请选择打卡场所")
    private Long placeId;

    /** 打卡类型：AUTO / ON_DUTY / OFF_DUTY / FIELD */
    @NotBlank(message = "请选择打卡类型")
    private String checkinType;

    /** 客户端定位（可选；提供则做围栏校验） */
    private BigDecimal lng;
    private BigDecimal lat;
}
