package com.lianzhi.attendance.face.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 人脸校验结果。
 */
@Getter
@Setter
public class FaceVerifyResult {

    private Long employeeId;
    private boolean passed;
    private double similarity;
    private double threshold;
    private String provider;
    private boolean mock;
    private String reason;
    private String notice;
}
