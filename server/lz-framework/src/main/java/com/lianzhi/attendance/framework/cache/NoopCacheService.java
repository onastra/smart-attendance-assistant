package com.lianzhi.attendance.framework.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 缓存关闭态实现：不做任何存储，每次都走真实查询。
 *
 * <p>刻意不做"内存 Map 假装缓存"：进程内缓存会让多实例部署时数据不一致，
 * 也会让人误以为缓存已经接好了。关闭就是关闭——慢一点，但结果永远是对的。</p>
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "lz.cache.enabled", havingValue = "false", matchIfMissing = true)
public class NoopCacheService implements CacheService {

    private final CacheStats stats = new CacheStats();

    public NoopCacheService() {
        log.warn("[CACHE] 缓存开关关闭（lz.cache.enabled=false），所有读写走数据库直查；"
                + "安装 Redis 并设置 lz.cache.enabled=true 后才会真正启用缓存");
    }

    @Override
    public String mode() {
        return "none";
    }

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public <T> T get(String key, Class<T> type) {
        stats.recordMiss();
        return null;
    }

    @Override
    public void put(String key, Object value, Duration ttl) {
        // 关闭态丢弃写入，不占用内存
    }

    @Override
    public void evict(String key) {
        // 无存储可删
    }

    @Override
    public void evictByPrefix(String prefix) {
        // 无存储可删
    }

    @Override
    public CacheStats stats() {
        return stats;
    }
}
