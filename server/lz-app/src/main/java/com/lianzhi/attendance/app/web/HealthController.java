package com.lianzhi.attendance.app.web;

import com.lianzhi.attendance.framework.cache.CacheService;
import com.lianzhi.attendance.framework.cache.CacheStats;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查。
 *
 * <p>该接口**必须真实探测**依赖可用性，不得直接返回 OK：数据库是否连得上、
 * 哪些外部能力被关闭，都要如实反映，避免演示时"看起来全绿但实际不可用"。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
@Tag(name = "系统运维")
public class HealthController {

    private final ObjectProvider<DataSource> dataSourceProvider;
    private final ObjectProvider<CacheService> cacheServiceProvider;

    @Value("${lz.cache.enabled:false}")
    private boolean cacheEnabled;
    @Value("${lz.nacos.enabled:false}")
    private boolean nacosEnabled;
    @Value("${lz.feign.enabled:false}")
    private boolean feignEnabled;
    @Value("${lz.face.provider:noop}")
    private String faceProvider;
    @Value("${lz.llm.enabled:false}")
    private boolean llmEnabled;
    @Value("${lz.map.provider:none}")
    private String mapProvider;

    @Operation(summary = "健康检查：返回应用与各项依赖的真实状态")
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("application", "lianzhi-attendance");
        body.put("status", "UP");
        body.put("serverTime", LocalDateTime.now().toString());
        body.put("database", probeDatabase());
        body.put("cache", probeCache());
        body.put("dependencies", Map.of(
                "redis", Map.of("enabled", cacheEnabled, "note", "关闭时应用不依赖 Redis，缓存逻辑降级"),
                "nacos", Map.of("enabled", nacosEnabled, "note", "已配置未启用（模块化单体，无需服务注册）"),
                "feign", Map.of("enabled", feignEnabled, "note", "单体内部无需 HTTP 调用自身"),
                "llm", Map.of("enabled", llmEnabled, "note", "未配置时知识库走 retrieval-only 模式"),
                "map", Map.of("provider", mapProvider, "note", "none 表示无地图服务 Key，坐标走手动录入"),
                "face", Map.of("provider", faceProvider, "mock", !"arcsoft".equalsIgnoreCase(faceProvider),
                        "note", "noop 表示模拟实现，界面与响应均标注 mock=true")
        ));
        return ResponseEntity.ok(body);
    }

    /**
     * 缓存状态：真实 PING 一次 Redis，并给出本进程内的命中统计。
     *
     * <p>命中率必须能看到，否则"接了缓存"和"缓存真在工作"混为一谈——
     * 长期 hit=0 说明键没命中或压根没走缓存路径。</p>
     */
    private Map<String, Object> probeCache() {
        CacheService cacheService = cacheServiceProvider.getIfAvailable();
        if (cacheService == null) {
            return Map.of("enabled", cacheEnabled, "mode", "unavailable", "note", "未装配 CacheService 实现");
        }
        boolean reachable = cacheEnabled && cacheService.available();
        CacheStats stats = cacheService.stats();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", cacheEnabled);
        body.put("mode", cacheService.mode());
        body.put("reachable", reachable);
        body.put("hit", stats.getHit());
        body.put("miss", stats.getMiss());
        body.put("write", stats.getWrite());
        body.put("error", stats.getError());
        body.put("fallback", stats.getFallback());
        body.put("hitRate", stats.hitRate());
        body.put("note", reachable
                ? "Redis 可用，缓存生效"
                : (cacheEnabled ? "开关已开但 Redis 不可达，已降级直查（不影响结果正确性）" : "缓存开关关闭"));
        return body;
    }

    /** 真实执行一次数据库连接，而不是只声明配置了数据源。 */
    private Map<String, Object> probeDatabase() {
        DataSource dataSource = dataSourceProvider.getIfAvailable();
        if (dataSource == null) {
            return Map.of("connected", false, "message", "未检测到数据源配置");
        }
        try (Connection connection = dataSource.getConnection()) {
            boolean valid = connection.isValid(2);
            String url = connection.getMetaData().getURL();
            return Map.of("connected", valid, "url", url);
        } catch (Exception e) {
            log.warn("数据库连通性检查失败：{}", e.getMessage());
            return Map.of("connected", false, "message", e.getMessage());
        }
    }
}
