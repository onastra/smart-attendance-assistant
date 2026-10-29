package com.lianzhi.attendance.framework.web;

import com.lianzhi.attendance.common.exception.BusinessException;
import com.lianzhi.attendance.common.result.R;
import com.lianzhi.attendance.common.result.ResultCode;
import com.lianzhi.attendance.common.util.TraceIdUtil;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

    /**
     * 业务异常：按原样返回码与文案，不打堆栈，避免日志噪音。
     *
     * <p>异常上可以携带 data（例如打卡失败的原因码 {@code reason}），
     * 必须透传出去：前端要据此区分"二维码过期"与"你不属于该场所"，
     * 只靠 message 文案做分支判断是不可靠的。</p>
     */
    @ExceptionHandler(BusinessException.class)
    public R<Object> handleBusinessException(BusinessException e) {
        String traceId = TraceIdUtil.generate();
        log.warn("[业务逻辑被拒绝] traceId={} code={} message={} data={}",
                traceId, e.getCode(), e.getMessage(), e.getData());
        if (e.getData() != null) {
            return R.fail(e.getCode(), e.getMessage(), e.getData()).traceId(traceId);
        }
        return R.fail(e.getCode(), e.getMessage()).traceId(traceId);
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

    /**
     * 路径不存在：API 请求返回 40401 而不是落进 50001 兜底；
     * 非 API 路径且打包了前端资源时转发到 index.html（SPA history 路由刷新支持）。
     *
     * <p>背景（2026-10-27 实测发现）：Spring Boot 3.2+ 对未匹配路径抛
     * {@link NoResourceFoundException}，此前它被下面的 Exception 兜底吞掉，
     * 访问不存在的接口得到 50001"系统内部错误"——既误导排障方向，
     * 又会让前端把"调错地址"当成"服务挂了"。</p>
     *
     * <p>2026-10-29 扩展：前后端合并部署（阿里云 FC 单函数方案）时，
     * Vue 用的是 history 路由，直接刷新 /attendance/records 这类深链接会
     * 先抛 NoResourceFoundException。约定：路径以 /api 开头才算接口 404，
     * 其余转发给前端入口页；classpath 里没有前端产物（static/index.html）
     * 时保持原 JSON 行为，避免 forward 循环。</p>
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public Object handleNotFound(HttpServletRequest request, Exception e) {
        String path = request.getRequestURI();
        boolean isApiPath = path.startsWith("/api/") || path.equals("/api");
        boolean frontendBundled = new ClassPathResource("static/index.html").exists();
        if (!isApiPath && frontendBundled && e instanceof NoResourceFoundException) {
            return new ModelAndView("forward:/index.html");
        }
        return R.fail(ResultCode.NOT_FOUND, "接口不存在：" + e.getMessage());
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
