package com.lianzhi.attendance.face.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 人脸识别能力状态：让界面能如实告诉用户"当前是模拟实现"以及为什么。
 */
@Getter
@Setter
public class FaceStatusResult {

    private String provider;
    private boolean mock;
    private boolean available;
    private boolean storeImage;
    private boolean enrolled;
    private String notice;
    private String privacyNote;
}
