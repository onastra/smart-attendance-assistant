package com.lianzhi.attendance.framework.config;

import com.lianzhi.attendance.framework.auth.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 层通用配置：拦截器注册与跨域放行。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    /**
     * 允许的前端来源。
     *
     * <p>用「模式」而不是「精确来源」：手机真机演示时前端跑在
     * {@code http://192.168.1.4:5173} 这类局域网地址上，IP 随 Wi-Fi 变化，
     * 逐个写死既不现实也会让演示当场失败（实测：Origin 不在白名单时
     * OPTIONS 预检直接 403，前端只看到"没有访问该资源的权限"，看不出是跨域问题）。</p>
     *
     * <p>精确来源与带 {@code *} 的模式可以混用，不含通配的模式等价于精确匹配。</p>
     */
    @Value("${lz.web.allowed-origins:http://localhost:*,http://127.0.0.1:*,http://192.168.*:*}")
    private String[] allowedOriginPatterns;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 只拦截 /api/**，接口文档与健康检查不进鉴权链路
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // allowedOriginPatterns（而非 allowedOrigins）：只有它支持 "http://192.168.*:*" 这类
        // 带通配符的来源，且仍然允许携带凭证（allowedOrigins("*") 会与 allowCredentials 冲突）。
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOriginPatterns)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
