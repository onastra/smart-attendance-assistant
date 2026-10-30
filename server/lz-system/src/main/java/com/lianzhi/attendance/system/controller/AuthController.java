package com.lianzhi.attendance.system.controller;

import com.lianzhi.attendance.common.result.R;
import com.lianzhi.attendance.framework.auth.AuthContext;
import com.lianzhi.attendance.framework.auth.AuthUser;
import com.lianzhi.attendance.framework.auth.RequireLogin;
import com.lianzhi.attendance.system.dto.LoginRequest;
import com.lianzhi.attendance.system.dto.LoginVO;
import com.lianzhi.attendance.system.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 认证相关接口。
 */
@Tag(name = "认证", description = "登录、登出与当前登录者信息")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "登录", description = "校验账号口令后签发 JWT；失败统一返回 401，不区分账号是否存在")
    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return R.ok(authService.login(request, resolveClientIp(httpRequest)));
    }

    @Operation(summary = "当前登录者信息", description = "依据请求头中的 Token 返回完整身份、角色与权限码（与登录返回同构，无 token）")
    @GetMapping("/profile")
    @RequireLogin
    public R<LoginVO> profile() {
        return R.ok(authService.profile(AuthContext.get()));
    }

    /**
     * 登出。
     *
     * <p>说明：JWT 是无状态的，服务端不维护黑名单，这里只在语义上提供出口，
     * 真正的失效由前端删除本地 token 完成。若将来要做强制下线，需要引入服务端令牌版本号。</p>
     */
    @Operation(summary = "登出", description = "前端清除本地令牌；当前 JWT 无状态，服务端不维护黑名单")
    @PostMapping("/logout")
    @RequireLogin
    public R<Void> logout() {
        return R.ok();
    }

    /**
     * 解析客户端 IP。代理场景下优先取 X-Forwarded-For 的第一段。
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
