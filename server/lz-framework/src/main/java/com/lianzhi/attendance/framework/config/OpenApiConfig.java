package com.lianzhi.attendance.framework.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档配置（springdoc，兼容 Spring Boot 3）。
 *
 * <p>启动后访问 http://localhost:8080/swagger-ui.html ，JSON 定义在 /v3/api-docs。</p>
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI lianzhiOpenApi() {
        SecurityScheme bearer = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");
        return new OpenAPI()
                .info(new Info()
                        .title("联智·企业级 AI Agent 智能考勤与业务助手 API")
                        .version("v1.0.0")
                        .description("模块化单体架构。模拟能力（人脸识别等）在响应中以 mock=true 明确标注。"))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME_NAME, bearer))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }
}
