package com.lianzhi.attendance.attendance.sign;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 二维码签名器的单元测试。
 *
 * <p>这是全系统防伪的基石：二维码图片会被截图、转发、拍照，属于完全不可控的公开信道。
 * 只要签名有任何"改一个字符还能通过"的缝隙，攻击者就能凭空造出任意场所的码，
 * 因此这里对"确定性""抗篡改""空签名不放行"三条逐一断言。</p>
 */
class QrSignerTest {

    private static final String SECRET = "unit-test-qr-sign-secret-0123456789abcdef";

    private final QrSigner signer = new QrSigner(SECRET);

    @Test
    @DisplayName("同一待签名串必须得到稳定相同的签名（确定性）")
    void signIsDeterministic() {
        String canonical = QrPayload.canonical(1L, "BIZ-0001", 1700000000L);
        String first = signer.sign(canonical);
        String second = signer.sign(canonical);

        assertEquals(first, second, "相同输入必须得到相同签名，否则生成与校验会互相误拒");
        assertEquals(64, first.length(), "HMAC-SHA256 的十六进制摘要应为 64 字符");
        assertTrue(first.matches("^[0-9a-f]{64}$"), "签名应为小写十六进制串");
    }

    @Test
    @DisplayName("改动场所 / 业务串 / 有效期任一字符，签名都必须不匹配")
    void tamperedPayloadMustBeRejected() {
        String canonical = QrPayload.canonical(1L, "BIZ-0001", 1700000000L);
        String sign = signer.sign(canonical);

        assertTrue(signer.matches(canonical, sign), "原文未被改动时必须通过");
        assertFalse(signer.matches(QrPayload.canonical(2L, "BIZ-0001", 1700000000L), sign),
                "改场所 ID 必须拒绝——否则可凭空造出其它场所的码");
        assertFalse(signer.matches(QrPayload.canonical(1L, "BIZ-0002", 1700000000L), sign),
                "改业务随机串必须拒绝");
        assertFalse(signer.matches(QrPayload.canonical(1L, "BIZ-0001", 1700000001L), sign),
                "改有效期必须拒绝——否则过期码可被无限续期");
    }

    @Test
    @DisplayName("空或空白签名一律拒绝，不允许降级放行")
    void blankSignatureMustBeRejected() {
        String canonical = QrPayload.canonical(1L, "BIZ-0001", 1700000000L);

        assertFalse(signer.matches(canonical, null), "null 签名不得放行");
        assertFalse(signer.matches(canonical, ""), "空串签名不得放行");
        assertFalse(signer.matches(canonical, "   "), "纯空白签名不得放行");
    }

    @Test
    @DisplayName("不同密钥签出的签名互不通用")
    void differentSecretProducesDifferentSign() {
        String canonical = QrPayload.canonical(1L, "BIZ-0001", 1700000000L);
        QrSigner other = new QrSigner("another-unit-test-secret-fedcba9876543210");

        assertFalse(signer.matches(canonical, other.sign(canonical)),
                "换密钥后旧签名必须失效，否则密钥轮换毫无意义");
    }

    @Test
    @DisplayName("签名大小写与前后空白不应影响校验结果为空——签名本身区分大小写")
    void signatureIsCaseSensitive() {
        String canonical = QrPayload.canonical(1L, "BIZ-0001", 1700000000L);
        String sign = signer.sign(canonical);

        assertFalse(signer.matches(canonical, sign.toUpperCase()),
                "十六进制签名大写化后必须校验失败，避免大小写折叠带来的碰撞面");
        assertTrue(signer.matches(canonical, "  " + sign + "  "),
                "二维码扫描常带入首尾空白，应容忍 trim 后比对");
    }
}
