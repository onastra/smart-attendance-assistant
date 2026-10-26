package com.lianzhi.attendance.face.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 模拟实现（明确标注）。
 *
 * <p><strong>它做什么</strong>：把"人脸图片"折算成图片指纹的 SHA-256 哈希，
 * 比对时按哈希字符的相同比例给一个相似度。对同一张图必然通过，对不同图必然不通过。</p>
 *
 * <p><strong>它不是什么</strong>：不是人脸检测，不是特征向量提取，不是活体检测。
 * 换个人、换个角度、用张照片都能骗过它——所以它没有任何身份凭证价值。
 * 这也是为什么响应里 {@code mock} 恒为 true、日志统一带 {@code [MOCK]} 前缀：
 * 让"这是模拟"这件事在任何一层都藏不住。</p>
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "lz.face", name = "provider", havingValue = "noop", matchIfMissing = true)
public class NoopFaceRecognitionService implements FaceRecognitionService {

    /** 模拟阈值：同一张图算出来是 1.0，不同图约 0.06，取 0.6 足以区分且留有余量 */
    private static final double MOCK_THRESHOLD = 0.6D;

    public NoopFaceRecognitionService() {
        log.warn("[MOCK] 人脸识别使用模拟实现（provider=noop）：不做真实人脸检测与特征比对，"
                + "结果不可作为身份凭证。真实 SDK 接入方式见 docs/face-sdk-integration.md");
    }

    @Override
    public String provider() {
        return "noop";
    }

    @Override
    public boolean mock() {
        return true;
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public String unavailableReason() {
        return null;
    }

    @Override
    public String extractFeature(byte[] imageBytes) {
        return sha256(("lz-face-mock|" + sha256(imageBytes)).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public double similarity(String featureA, String featureB) {
        if (featureA == null || featureB == null || featureA.length() != featureB.length()) {
            return 0D;
        }
        int same = 0;
        for (int i = 0; i < featureA.length(); i++) {
            if (featureA.charAt(i) == featureB.charAt(i)) {
                same++;
            }
        }
        return (double) same / featureA.length();
    }

    @Override
    public double threshold() {
        return MOCK_THRESHOLD;
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
