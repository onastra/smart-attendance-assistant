package com.lianzhi.attendance.common.exception;

import com.lianzhi.attendance.common.result.ResultCode;
import lombok.Getter;

import java.io.Serial;

/**
 * 业务异常。
 *
 * <p>可预期的业务失败（参数不合法、状态冲突、无权限等）都应抛出本异常，
 * 由统一异常处理器转换成对应的 ResultCode，避免在 Controller 里散落 try-catch。</p>
 */
@Getter
public class BusinessException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int code;
    private final ResultCode resultCode;
    private final transient Object data;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
        this.code = resultCode.getCode();
        this.data = null;
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
        this.code = resultCode.getCode();
        this.data = null;
    }

    public BusinessException(ResultCode resultCode, String message, Object data) {
        super(message);
        this.resultCode = resultCode;
        this.code = resultCode.getCode();
        this.data = data;
    }

    public BusinessException(ResultCode resultCode, Object data) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
        this.code = resultCode.getCode();
        this.data = data;
    }
}
