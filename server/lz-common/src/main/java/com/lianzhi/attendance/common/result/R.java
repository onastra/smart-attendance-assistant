package com.lianzhi.attendance.common.result;

import lombok.Getter;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;

/**
 * 全系统统一响应体。
 *
 * <p>约定：<code>code == 0</code> 表示成功；任何非 0 情况下 data 语义由 ResultCode 决定，
 * 前端不得忽略 code 只看 data 是否存在。</p>
 */
@Getter
@ToString
public class R<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Getter
    @ToString
    public static class FieldError implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private final String field;
        private final String message;

        public FieldError(String field, String message) {
            this.field = field;
            this.message = message;
        }
    }

    private final int code;
    private final String message;
    private final T data;
    private final String traceId;
    private final long timestamp;

    private R(int code, String message, T data, String traceId) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = traceId;
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> R<T> ok() {
        return new R<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null, null);
    }

    public static <T> R<T> ok(T data) {
        return new R<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data, null);
    }

    public static <T> R<T> fail(ResultCode resultCode) {
        return new R<>(resultCode.getCode(), resultCode.getMessage(), null, null);
    }

    public static <T> R<T> fail(ResultCode resultCode, String message) {
        return new R<>(resultCode.getCode(), message, null, null);
    }

    public static <T> R<T> fail(ResultCode resultCode, String message, T data) {
        return new R<>(resultCode.getCode(), message, data, null);
    }

    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null, null);
    }

    /** 填充链路追踪 ID，便于前端报错后能凭 ID 在日志中定位。 */
    public R<T> traceId(String traceId) {
        return new R<>(this.code, this.message, this.data, traceId);
    }

    public boolean isSuccess() {
        return this.code == ResultCode.SUCCESS.getCode();
    }
}
