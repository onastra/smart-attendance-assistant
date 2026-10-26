package com.lianzhi.attendance.common.result;

import lombok.Getter;

/**
 * 统一业务状态码。
 *
 * <p>设计原因：前后端依赖 code 做程序分支（如 401 跳登录页），不能只靠 message 文案判断，
 * 因此把 code 收敛到枚举统一管理，避免各处散落魔法数字。</p>
 */
@Getter
public enum ResultCode {

    SUCCESS(0, "OK"),

    PARAM_ERROR(40001, "参数校验失败"),
    UNAUTHORIZED(40101, "未登录或登录状态已失效"),
    FORBIDDEN(40301, "没有访问该资源的权限"),
    NOT_FOUND(40401, "资源不存在"),
    CONFLICT(40901, "业务冲突：数据重复或当前状态不允许该操作"),
    TOO_MANY_REQUESTS(42901, "请求过于频繁，请稍后再试"),

    INTERNAL_ERROR(50001, "系统内部错误"),
    DEPENDENCY_NOT_CONFIGURED(50301, "依赖的外部能力未配置或未启用");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
