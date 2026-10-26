package com.lianzhi.attendance.attendance.sign;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 二维码签名器：HMAC-SHA256。
 *
 * <p>密钥来自 <code>lz.attendance.qr-sign-secret</code>，
 * 与 JWT 密钥分开配置——二者用途不同，共用一把密钥会让任一泄露同时影响认证与考勤防伪。</p>
 */
@Slf4j
@Component
public class QrSigner {

    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] secretBytes;

    public QrSigner(@Value("${lz.attendance.qr-sign-secret:lianzhi-qr-default-secret-please-change}") String secret) {
        this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (this.secretBytes.length < 16) {
            log.warn("[QR] 二维码签名密钥长度不足 16 字节，建议通过 LZ_QR_SIGN_SECRET 配置更长的密钥");
        }
    }

    public String sign(String canonical) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secretBytes, ALGORITHM));
            byte[] raw = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            return toHex(raw);
        } catch (Exception e) {
            // 签名失败必须让请求失败，绝不能降级为"放行"
            throw new IllegalStateException("二维码签名失败", e);
        }
    }

    /**
     * 校验签名。
     *
     * <p>使用 {@link MessageDigest#isEqual} 做常量时间比较：
     * 字符串 equals 在首个不同字符处立即返回，会泄露"前几位已经猜对"的信息，
     * 虽然对二维码场景的实际威胁有限，但这是安全编码的底线习惯。</p>
     */
    public boolean matches(String canonical, String expectedSign) {
        if (expectedSign == null || expectedSign.isBlank()) {
            return false;
        }
        String actual = sign(canonical);
        return MessageDigest.isEqual(
                actual.getBytes(StandardCharsets.UTF_8),
                expectedSign.trim().getBytes(StandardCharsets.UTF_8));
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
