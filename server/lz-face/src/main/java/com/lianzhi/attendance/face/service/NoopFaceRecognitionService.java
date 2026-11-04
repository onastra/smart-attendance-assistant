package com.lianzhi.attendance.face.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 模拟实现（明确标注）。
 *
 * <p><strong>它做什么</strong>：把"人脸图片"折算成<strong>感知哈希（dHash）指纹</strong>——
 * 缩到 9x8 灰度后取相邻像素梯度方向，得到 64 位指纹；另取画面中心区域（人脸所在位置）
 * 再算一份 64 位指纹，两者拼接存库。比对时按汉明距离换算相似度，
 * 中心指纹权重更高。对<strong>同一人的重拍</strong>（光线、压缩、轻微角度变化）天然容忍，
 * 对<strong>完全不同的图</strong>（如风景照）会给出低相似度。</p>
 *
 * <p><strong>它不是什么</strong>：仍然不是人脸检测、不是特征向量提取、不是活体检测。
 * 两个不同的人在各拍一张正脸照时，感知哈希也可能足够接近——所以它不能精确区分"换人"，
 * 更挡不住照片攻击，没有任何身份凭证价值。
 * 这也是为什么响应里 {@code mock} 恒为 true、日志统一带 {@code [MOCK]} 前缀：
 * 让"这是模拟"这件事在任何一层都藏不住。真实活体识别请用 provider=arcsoft。</p>
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "lz.face", name = "provider", havingValue = "noop", matchIfMissing = true)
public class NoopFaceRecognitionService implements FaceRecognitionService {

    /**
     * 模拟阈值：同人重拍（暗光/压缩/轻微裁剪）组合相似度实测 0.70+，风景等异类图约 0.47。
     * 取 0.65 保证模拟模式下同人重拍稳定通过；注意 mock 对"构图相似的另一人"无区分力（原理局限）。
     */
    private static final double MOCK_THRESHOLD = 0.65D;

    /** 中心区域权重：人脸居中，中心指纹比整图指纹更能区分内容差异 */
    private static final double CENTER_WEIGHT = 0.6D;

    /** 新版感知哈希特征前缀（旧版 SHA-256 特征无前缀，比对时走遗留降级） */
    private static final String PREFIX = "dh:";

    public NoopFaceRecognitionService() {
        log.warn("[MOCK] 人脸识别使用模拟实现（provider=noop，感知哈希比对）：不做真实人脸检测、"
                + "特征提取与活体检测，结果不可作为身份凭证。真实 SDK 接入方式见 docs/face-sdk-integration.md");
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
        long[] hashes = perceptualHashes(imageBytes);
        if (hashes == null) {
            // ImageIO 不可用或图片无法解码时，降级为旧版加密哈希（只保证同图通过）
            return sha256(("lz-face-mock|" + sha256(imageBytes)).getBytes(StandardCharsets.UTF_8));
        }
        return PREFIX + hex16(hashes[0]) + hex16(hashes[1]);
    }

    @Override
    public double similarity(String featureA, String featureB) {
        if (featureA == null || featureB == null) {
            return 0D;
        }
        if (featureA.startsWith(PREFIX) && featureB.startsWith(PREFIX)
                && featureA.length() == PREFIX.length() + 32
                && featureB.length() == PREFIX.length() + 32) {
            long[] a = {hexToLong(featureA, PREFIX.length()), hexToLong(featureA, PREFIX.length() + 16)};
            long[] b = {hexToLong(featureB, PREFIX.length()), hexToLong(featureB, PREFIX.length() + 16)};
            double full = (64D - Long.bitCount(a[0] ^ b[0])) / 64D;
            double center = (64D - Long.bitCount(a[1] ^ b[1])) / 64D;
            return (1 - CENTER_WEIGHT) * full + CENTER_WEIGHT * center;
        }
        // 遗留 SHA-256 特征（模拟期旧数据）：保持原字符比例算法，不同图必然低于阈值，促使重新注册
        if (featureA.length() != featureB.length()) {
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

    /**
     * dHash 感知哈希：整图一份 + 中心裁剪一份。
     * 算法：缩放到 9x8 灰度，逐行比较右像素是否大于左像素，得 64 位指纹。
     * 返回 null 表示图片无法解码或图像环境不可用。
     */
    private long[] perceptualHashes(byte[] imageBytes) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (src == null || src.getWidth() <= 0 || src.getHeight() <= 0) {
                return null;
            }
            long full = dHash(src);
            // 中心裁剪：横向取中间 50%，纵向取中间 60%（正脸照人脸基本落在此区域）
            int cw = Math.max(1, src.getWidth() / 4);
            int ch = Math.max(1, src.getHeight() * 3 / 10);
            BufferedImage center = src.getSubimage(cw, ch, src.getWidth() - 2 * cw, src.getHeight() - 2 * ch);
            long centerHash = dHash(center);
            return new long[]{full, centerHash};
        } catch (Throwable t) {
            // java.desktop 缺失、图片损坏等一切异常都降级，绝不让模拟比对拖垮注册流程
            log.warn("[MOCK] 感知哈希计算失败，降级为加密哈希指纹: {}", t.toString());
            return null;
        }
    }

    private long dHash(BufferedImage img) {
        BufferedImage gray = new BufferedImage(9, 8, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gray.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, 9, 8);
            g.drawImage(img, 0, 0, 9, 8, null);
        } finally {
            g.dispose();
        }
        // TYPE_BYTE_GRAY 直接绘制可能仍带色彩管理，统一再做一次显式灰度转换
        BufferedImage pure = new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null)
                .filter(gray, new BufferedImage(9, 8, BufferedImage.TYPE_BYTE_GRAY));
        long hash = 0L;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                hash <<= 1;
                if (luma(pure, x + 1, y) > luma(pure, x, y)) {
                    hash |= 1L;
                }
            }
        }
        return hash;
    }

    private int luma(BufferedImage gray, int x, int y) {
        return gray.getRaster().getSample(x, y, 0) & 0xFF;
    }

    private String hex16(long v) {
        return String.format("%016x", v);
    }

    private long hexToLong(String s, int from) {
        return Long.parseUnsignedLong(s.substring(from, from + 16), 16);
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
