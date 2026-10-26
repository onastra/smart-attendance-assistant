package com.lianzhi.attendance.framework.web;

import com.lianzhi.attendance.common.exception.BusinessException;
import com.lianzhi.attendance.common.result.R;
import com.lianzhi.attendance.common.result.ResultCode;
import com.lianzhi.attendance.common.util.TraceIdUtil;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一异常处理。
 *
 * <p>目标：任何异常都转换成约定好的 code/message，**不把堆栈直接暴露给前端**，
 * 同时用 traceId 把前后端日志串起来。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：按原样返回码与文案，不打堆栈，避免日志噪音。 */
    @ExceptionHandler(BusinessException.class)
    public R<Void> handleBusinessException(BusinessException e) {
        String traceId = TraceIdUtil.generate();
        log.warn("[业务逻辑被拒绝] traceId={} code={} message={}", traceId, e.getCode(), e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    /** JSON 体参数校验失败（@RequestBody + @Valid）。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Map<String, Object>> handleValidException(MethodArgumentNotValidException e) {
        List<R.FieldError> fieldErrors = new ArrayList<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            fieldErrors.add(new R.FieldError(fieldError.getField(), fieldError.getDefaultMessage()));
        }
        return R.fail(ResultCode.PARAM_ERROR, ResultCode.PARAM_ERROR.getMessage(),
                Map.of("fieldErrors", fieldErrors));
    }

    /** 查询参数/路径参数校验失败（@Validated）。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public R<Map<String, Object>> handleConstraintViolation(ConstraintViolationException e) {
        List<R.FieldError> fieldErrors = new ArrayList<>();
        for (ConstraintViolation<?> violation : e.getConstraintViolations()) {
            String field = String.valueOf(violation.getPropertyPath());
            fieldErrors.add(new R.FieldError(field, violation.getMessage()));
        }
        return R.fail(ResultCode.PARAM_ERROR, ResultCode.PARAM_ERROR.getMessage(),
                Map.of("fieldErrors", fieldErrors));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, HttpRequestMethodNotSupportedException.class,
            HttpMessageNotReadableException.class})
    public R<Void> handleBadRequest(Exception e) {
        return R.fail(ResultCode.PARAM_ERROR, "请求参数有误：" + e.getMessage());
    }

    /** 兜底：未知异常按内部错误返回，并把 traceId 交给前端便于追查。 */
    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e) {
        String traceId = TraceIdUtil.generate();
        log.error("[未处理异常] traceId={}", traceId, e);
        return R.fail(ResultCode.INTERNAL_ERROR,
                ResultCode.INTERNAL_ERROR.getMessage() + "（traceId=" + traceId + "）");
    }
}
