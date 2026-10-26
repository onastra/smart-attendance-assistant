package com.lianzhi.attendance.framework.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis 缓存实现（{@code lz.cache.enabled=true} 时装配）。
 *
 * <p>三条降级规则，都是为了避免"Redis 出故障导致业务不可用"：
 * <ol>
 *   <li>单次读写异常 → 吞掉异常、记 WARN、本次走直查；</li>
 *   <li>连续失败达到阈值 → 进入 30 秒熔断，期间不再访问 Redis（否则每个请求都要等连接超时）；</li>
 *   <li>熔断结束后自动尝试恢复，恢复成功即清除熔断标记。</li>
 * </ol>
 * 缓存里只存 JSON 字符串，可读可排查；键统一带前缀，便于按前缀整体失效。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "lz.cache.enabled", havingValue = "true")
public class RedisCacheService implements CacheService {

    /** 连续失败多少次后熔断 */
    private static final int FAILURE_THRESHOLD = 3;
    /** 熔断持续时间：够长以跳过一次网络抖动，够短以便及时恢复 */
    private static final long COOLDOWN_MILLIS = 30_000L;
    private static final int SCAN_COUNT = 200;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String keyPrefix;
    private final CacheStats stats = new CacheStats();

    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong degradedUntil = new AtomicLong();
    private final AtomicBoolean degradedLogged = new AtomicBoolean(false);

    public RedisCacheService(StringRedisTemplate redisTemplate,
                             ObjectMapper objectMapper,
                             @Value("${lz.cache.key-prefix:lz:cache:v1:}") String keyPrefix) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.keyPrefix = keyPrefix;
        log.info("[CACHE] 缓存已启用：Redis 模式，键前缀 {}", keyPrefix);
    }

    @Override
    public String mode() {
        return degraded() ? "redis(degraded)" : "redis";
    }

    @Override
    public boolean available() {
        return ping();
    }

    @Override
    public <T> T get(String key, Class<T> type) {
        if (degraded()) {
            stats.recordFallback();
            return null;
        }
        String fullKey = keyPrefix + key;
        try {
            String json = redisTemplate.opsForValue().get(fullKey);
            if (json == null) {
                stats.recordMiss();
                recover();
                return null;
            }
            T value = objectMapper.readValue(json, type);
            stats.recordHit();
            recover();
            return value;
        } catch (Exception e) {
            handleFailure("get", fullKey, e);
            return null;
        }
    }

    @Override
    public void put(String key, Object value, Duration ttl) {
        if (value == null || degraded()) {
            return;
        }
        String fullKey = keyPrefix + key;
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(fullKey, json, ttl);
            stats.recordWrite();
            recover();
        } catch (Exception e) {
            handleFailure("put", fullKey, e);
        }
    }

    @Override
    public void evict(String key) {
        if (degraded()) {
            return;
        }
        try {
            redisTemplate.delete(keyPrefix + key);
            recover();
        } catch (Exception e) {
            handleFailure("evict", keyPrefix + key, e);
        }
    }

    /**
     * 按前缀失效。
     *
     * <p>用 SCAN 而不是 KEYS：KEYS 在大键空间下会阻塞 Redis 单线程，
     * 这类"清理缓存把自己打挂"的事故很常见，这里从一开始就避开。</p>
     */
    @Override
    public void evictByPrefix(String prefix) {
        if (degraded()) {
            return;
        }
        String pattern = keyPrefix + prefix + "*";
        try {
            Set<String> keys = new HashSet<>();
            redisTemplate.execute((RedisCallback<Set<String>>) connection -> {
                try (Cursor<byte[]> cursor = connection.keyCommands()
                        .scan(ScanOptions.scanOptions().match(pattern).count(SCAN_COUNT).build())) {
                    while (cursor.hasNext()) {
                        keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                    }
                }
                return keys;
            });
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
            recover();
        } catch (Exception e) {
            handleFailure("evictByPrefix", pattern, e);
        }
    }

    @Override
    public CacheStats stats() {
        return stats;
    }

    /** 真实 PING 一次，不做"配置了就算通"的判断。 */
    private boolean ping() {
        try {
            String pong = redisTemplate.execute((RedisCallback<String>) connection -> connection.ping());
            boolean ok = "PONG".equalsIgnoreCase(pong);
            if (ok) {
                recover();
            }
            return ok;
        } catch (Exception e) {
            log.warn("[CACHE] Redis PING 失败：{}", e.getMessage());
            return false;
        }
    }

    private boolean degraded() {
        return System.currentTimeMillis() < degradedUntil.get();
    }

    private void handleFailure(String operation, String key, Exception e) {
        stats.recordError();
        stats.recordFallback();
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= FAILURE_THRESHOLD) {
            degradedUntil.set(System.currentTimeMillis() + COOLDOWN_MILLIS);
            if (degradedLogged.compareAndSet(false, true)) {
                log.warn("[CACHE] Redis 连续失败 {} 次，进入 {}s 熔断，期间全部直查数据库：{}",
                        failures, COOLDOWN_MILLIS / 1000, e.getMessage());
            }
            return;
        }
        log.warn("[CACHE] Redis {} 失败（key={}），本次降级直查：{}", operation, key, e.getMessage());
    }

    /** 一次成功即视为恢复：清除连续失败计数与熔断标记。 */
    private void recover() {
        if (consecutiveFailures.get() != 0) {
            consecutiveFailures.set(0);
        }
        if (degradedUntil.get() != 0) {
            degradedUntil.set(0);
            if (degradedLogged.compareAndSet(true, false)) {
                log.info("[CACHE] Redis 已恢复，退出熔断，缓存重新开始生效");
            }
        }
    }
}
