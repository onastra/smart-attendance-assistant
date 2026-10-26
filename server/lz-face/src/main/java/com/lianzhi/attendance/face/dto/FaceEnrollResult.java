package com.lianzhi.attendance.face.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 人脸录入结果。
 *
 * <p>{@code mock} 恒为 true 时表示这条特征值来自模拟实现，
 * 绝不能当作真实生物特征使用——前端与日志都必须原样展示这个标记。</p>
 */
@Getter
@Setter
public class FaceEnrollResult {

    private Long employeeId;
    private String provider;
    private boolean mock;
    private String featureHashPreview;
    private boolean storedImage;
    private String notice;
}
