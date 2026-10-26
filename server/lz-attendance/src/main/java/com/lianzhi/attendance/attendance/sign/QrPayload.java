package com.lianzhi.attendance.attendance.sign;

import java.util.HashMap;
import java.util.Map;

/**
 * 二维码原文。
 *
 * <p>格式（v1）：<code>LZ1|p={placeId}|b={bizId}|t={expireAtEpochSecond}|s={sign}</code></p>
 *
 * <p>为什么要自己定义格式而不是直接把数据库主键塞进二维码：
 * 二维码一旦生成就会被截图、转发、拍照，属于完全不可控的公开信道。
 * 只放一个自增 ID 意味着任何人把 ID 加 1 就能伪造另一个场所的码。
 * 因此这里采用"明文参数 + 服务端签名"的方案：
 * 参数可以被看见，但改任何一个字符签名就对不上，服务端校验直接拒绝。</p>
 */
public final class QrPayload {

    private static final String PREFIX = "LZ1";
    private static final String VERSION_PREFIX = PREFIX + "|";
    private static final String KEY_PLACE = "p";
    private static final String KEY_BIZ = "b";
    private static final String KEY_EXPIRE = "t";
    private static final String KEY_SIGN = "s";

    private final Long placeId;
    private final String bizId;
    private final Long expireAtEpochSecond;
    private final String sign;

    private QrPayload(Long placeId, String bizId, Long expireAtEpochSecond, String sign) {
        this.placeId = placeId;
        this.bizId = bizId;
        this.expireAtEpochSecond = expireAtEpochSecond;
        this.sign = sign;
    }

    /**
     * 构造待签名串（不含签名本身）。
     *
     * <p>签名对象必须是确定性的：字段顺序固定、不使用 Map 遍历、不做 URL 编码，
     * 否则"生成时算出 A、校验时算出 B"会造成全量误拒。</p>
     */
    public static String canonical(Long placeId, String bizId, long expireAtEpochSecond) {
        return PREFIX
                + "|" + KEY_PLACE + "=" + placeId
                + "|" + KEY_BIZ + "=" + bizId
                + "|" + KEY_EXPIRE + "=" + expireAtEpochSecond;
    }

    /** 解析二维码原文；格式不合法时抛出带原因的非法参数异常。 */
    public static QrPayload parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("二维码内容为空");
        }
        String text = raw.trim();
        if (!text.startsWith(VERSION_PREFIX)) {
            throw new IllegalArgumentException("不是本系统的考勤二维码");
        }
        Map<String, String> kv = new HashMap<>();
        for (String part : text.split("\\|")) {
            int eq = part.indexOf('=');
            if (eq > 0 && eq < part.length() - 1) {
                kv.put(part.substring(0, eq), part.substring(eq + 1));
            }
        }
        Long placeId = parseLong(kv.get(KEY_PLACE), "场所 ID");
        Long expireAt = parseLong(kv.get(KEY_EXPIRE), "有效期");
        String bizId = kv.get(KEY_BIZ);
        String sign = kv.get(KEY_SIGN);
        if (bizId == null || bizId.isBlank()) {
            throw new IllegalArgumentException("二维码缺少业务随机串");
        }
        if (sign == null || sign.isBlank()) {
            throw new IllegalArgumentException("二维码缺少签名");
        }
        return new QrPayload(placeId, bizId, expireAt, sign);
    }

    /** 当前对象的待签名串（重新按固定顺序拼装，不复用原始文本，避免空白字符绕过） */
    public String canonical() {
        return canonical(placeId, bizId, expireAtEpochSecond);
    }

    /** 完整原文（含签名），即写进二维码图片的内容 */
    public String serialize() {
        return canonical() + "|" + KEY_SIGN + "=" + sign;
    }

    private static Long parseLong(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("二维码缺少" + fieldName);
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("二维码" + fieldName + "格式非法");
        }
    }

    public Long getPlaceId() {
        return placeId;
    }

    public String getBizId() {
        return bizId;
    }

    public Long getExpireAtEpochSecond() {
        return expireAtEpochSecond;
    }

    public String getSign() {
        return sign;
    }
}
