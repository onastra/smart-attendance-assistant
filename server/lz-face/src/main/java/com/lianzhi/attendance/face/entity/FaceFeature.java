package com.lianzhi.attendance.face.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 人脸特征记录。
 *
 * <p><strong>最小化存储</strong>：只存特征值的哈希，默认不存原始人脸图片
 * （配置项 {@code lz.face.store-image}，默认 false）。生物特征一旦泄漏无法更换，
 * 能不存就不存——这不是保守，是这类数据的基本要求。</p>
 */
@Getter
@Setter
public class FaceFeature {

    private Long id;
    private Long employeeId;
    /** noop / arcsoft */
    private String provider;
    private String featureHash;
    /** 1 = 由模拟实现产生，绝不能当作真实生物特征使用 */
    private Integer mockFlag;
    private LocalDateTime enrollTime;
    private Integer status;

    /** 冗余展示字段 */
    private String employeeName;
    private String empNo;
}
