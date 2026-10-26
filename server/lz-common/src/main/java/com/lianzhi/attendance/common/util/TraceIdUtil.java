package com.lianzhi.attendance.common.util;

import java.util.UUID;

/**
 * 轻量 ID 工具。
 *
 * <p>traceId 用于在响应体和日志之间建立关联：一次失败的打卡请求，
 * 前端拿到的 traceId 能直接在后端日志中定位到同一条记录。</p>
 */
public final class TraceIdUtil {

    private TraceIdUtil() {
    }

    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
