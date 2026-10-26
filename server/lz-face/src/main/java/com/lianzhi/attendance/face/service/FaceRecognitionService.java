package com.lianzhi.attendance.face.service;

/**
 * 人脸识别服务接口。
 *
 * <p>这一层是<strong>模块 G 的隔离带</strong>：上层只依赖接口，
 * 换真实 SDK 时只要新增一个实现并改配置项，业务代码零改动。
 * 这也是"无 SDK 授权"条件下唯一诚实的做法——把不可得的能力封在适配层里，
 * 并在每一处返回值与日志上标注它是模拟的。</p>
 */
public interface FaceRecognitionService {

    /** 实现标识：noop / arcsoft */
    String provider();

    /** true = 模拟实现，结果不可作为真实身份凭证 */
    boolean mock();

    /** 当前实现是否真的可用（缺 SDK / 缺授权时为 false） */
    boolean available();

    /** 不可用时给用户的说明；可用时为 null */
    String unavailableReason();

    /** 由图片算出特征值（真实实现为人脸特征向量，模拟实现为图片指纹的哈希） */
    String extractFeature(byte[] imageBytes);

    /** 两个特征值的相似度，取值 0~1 */
    double similarity(String featureA, String featureB);

    /** 判定通过的相似度阈值 */
    double threshold();
}
