package com.lianzhi.attendance.framework.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记该接口需要的权限码（如 <code>attendance:record:query</code>）。
 *
 * <p>权限校验发生在服务端拦截器中，**前端隐藏按钮只是体验优化，不构成安全边界**。</p>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {

    String value();
}
