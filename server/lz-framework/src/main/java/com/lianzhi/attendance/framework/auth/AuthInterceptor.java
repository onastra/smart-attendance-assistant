package com.lianzhi.attendance.framework.auth;

import com.lianzhi.attendance.common.exception.BusinessException;
import com.lianzhi.attendance.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;

/**
 * 登录态与权限拦截器。
 *
 * <p>执行顺序：解析 token → 写入 AuthContext → 校验注解声明的权限码。
 * 未标注任何注解的接口不做强制登录校验（登录接口、健康检查、接口文档等已在配置中排除）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenService tokenService;
    private final ObjectProvider<PermissionChecker> permissionCheckerProvider;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequireLogin requireLogin = findAnnotation(handlerMethod, RequireLogin.class);
        RequirePermission requirePermission = findAnnotation(handlerMethod, RequirePermission.class);
        if (requireLogin == null && requirePermission == null) {
            resolveUser(request).ifPresent(AuthContext::set);
            return true;
        }

        AuthUser user = resolveUser(request)
                .orElseThrow(() -> new BusinessException(ResultCode.UNAUTHORIZED));
        AuthContext.set(user);

        if (requirePermission != null) {
            PermissionChecker checker = permissionCheckerProvider.getIfAvailable();
            if (checker == null) {
                // 权限数据源缺失时按拒绝处理，避免"没有校验器就等于通过"
                log.error("[PERM] 未找到 PermissionChecker 实现，拒绝需要权限 {} 的请求", requirePermission.value());
                throw new BusinessException(ResultCode.FORBIDDEN, "权限服务不可用，请联系管理员");
            }
            if (!checker.hasPermission(user.getUserId(), requirePermission.value())) {
                log.warn("[PERM] userId={} 缺少权限 {}", user.getUserId(), requirePermission.value());
                throw new BusinessException(ResultCode.FORBIDDEN);
            }
            user.addPermissions(checker.permissionsOf(user.getUserId()));
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        AuthContext.clear();
    }

    private <A extends java.lang.annotation.Annotation> A findAnnotation(HandlerMethod handlerMethod, Class<A> type) {
        A onMethod = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), type);
        return onMethod != null
                ? onMethod
                : AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), type);
    }

    private Optional<AuthUser> resolveUser(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        return tokenService.parse(header.substring(BEARER_PREFIX.length()).trim());
    }
}
