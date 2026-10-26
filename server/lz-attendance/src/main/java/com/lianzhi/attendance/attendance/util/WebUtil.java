package com.lianzhi.attendance.attendance.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Web 辅助方法。
 */
public final class WebUtil {

    private WebUtil() {
    }

    /**
     * 取客户端 IP。
     *
     * <p>经过代理时优先取 X-Forwarded-For 的第一个地址，但也只是"尽力而为"：
     * 该头可以被伪造，因此它只用于留痕展示，不作为任何安全判定依据。</p>
     */
    public static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) {
            return real.trim();
        }
        return request.getRemoteAddr();
    }
}
