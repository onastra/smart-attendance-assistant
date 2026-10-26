package com.lianzhi.attendance.attendance.sign;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 二维码原文编解码的单元测试。
 *
 * <p>解析入口直接面对"用户扫到的任意字符串"，包括别家系统的码、随手拍的网址、
 * 被截断的文本。因此每种畸形输入都要给出明确原因，而不是抛空指针或静默通过。</p>
 */
class QrPayloadTest {

    private final QrSigner signer = new QrSigner("unit-test-qr-sign-secret-0123456789abcdef");

    @Test
    @DisplayName("序列化后再解析应得到完全相同的字段（往返一致）")
    void serializeThenParseRoundTrip() {
        String canonical = QrPayload.canonical(12L, "BIZ-ABCDEF", 1700000000L);
        String raw = canonical + "|s=" + signer.sign(canonical);

        QrPayload parsed = QrPayload.parse(raw);

        assertEquals(12L, parsed.getPlaceId());
        assertEquals("BIZ-ABCDEF", parsed.getBizId());
        assertEquals(1700000000L, parsed.getExpireAtEpochSecond());
        assertEquals(signer.sign(canonical), parsed.getSign());
        assertEquals(raw, parsed.serialize(), "往返序列化必须可复原");
    }

    @Test
    @DisplayName("待签名串按固定顺序拼装，且不含签名字段本身")
    void canonicalIsDeterministicAndExcludesSign() {
        String canonical = QrPayload.canonical(1L, "BIZ-1", 1700000000L);

        assertEquals("LZ1|p=1|b=BIZ-1|t=1700000000", canonical);
        assertEquals(canonical, QrPayload.canonical(1L, "BIZ-1", 1700000000L));
    }

    @Test
    @DisplayName("解析出的对象重新拼装待签名串，应忽略原文中的空白干扰")
    void parsedCanonicalIgnoresWhitespaceInRawText() {
        String canonical = QrPayload.canonical(3L, "BIZ-XY", 1700000001L);
        String raw = "  " + canonical + "|s=" + signer.sign(canonical) + "  ";

        QrPayload parsed = QrPayload.parse(raw);

        assertEquals(canonical, parsed.canonical(), "必须用字段重拼，而不是复用原始文本");
        assertTrue(signer.matches(parsed.canonical(), parsed.getSign()));
    }

    @Test
    @DisplayName("空内容 / 非本系统前缀：拒绝并给出原因")
    void rejectEmptyOrForeignContent() {
        assertThrows(IllegalArgumentException.class, () -> QrPayload.parse(null));
        assertThrows(IllegalArgumentException.class, () -> QrPayload.parse(""));
        assertThrows(IllegalArgumentException.class, () -> QrPayload.parse("   "));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> QrPayload.parse("https://example.com/promo?id=123"));
        assertTrue(ex.getMessage().contains("不是本系统"), "应明确提示不是本系统的码，实际：" + ex.getMessage());
    }

    @Test
    @DisplayName("缺少关键字段：拒绝并指出缺什么")
    void rejectMissingFields() {
        IllegalArgumentException noPlace = assertThrows(IllegalArgumentException.class,
                () -> QrPayload.parse("LZ1|b=BIZ-1|t=1700000000|s=abc"));
        assertTrue(noPlace.getMessage().contains("场所"), "实际：" + noPlace.getMessage());

        IllegalArgumentException noExpire = assertThrows(IllegalArgumentException.class,
                () -> QrPayload.parse("LZ1|p=1|b=BIZ-1|s=abc"));
        assertTrue(noExpire.getMessage().contains("有效期"), "实际：" + noExpire.getMessage());

        IllegalArgumentException noBiz = assertThrows(IllegalArgumentException.class,
                () -> QrPayload.parse("LZ1|p=1|t=1700000000|s=abc"));
        assertTrue(noBiz.getMessage().contains("业务随机串"), "实际：" + noBiz.getMessage());

        IllegalArgumentException noSign = assertThrows(IllegalArgumentException.class,
                () -> QrPayload.parse("LZ1|p=1|b=BIZ-1|t=1700000000"));
        assertTrue(noSign.getMessage().contains("签名"), "实际：" + noSign.getMessage());
    }

    @Test
    @DisplayName("字段类型非法：拒绝而不是解析出 0 或负数")
    void rejectMalformedNumbers() {
        assertThrows(IllegalArgumentException.class, () -> QrPayload.parse("LZ1|p=abc|b=BIZ-1|t=1|s=abc"));
        assertThrows(IllegalArgumentException.class, () -> QrPayload.parse("LZ1|p=1|b=BIZ-1|t=xyz|s=abc"));

        QrPayload ok = QrPayload.parse("LZ1|p=1|b=BIZ-1|t=1700000000|s=abc");
        assertNotNull(ok);
        assertEquals(1L, ok.getPlaceId());
    }
}
