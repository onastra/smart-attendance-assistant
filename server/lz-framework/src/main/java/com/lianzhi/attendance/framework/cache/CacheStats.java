package com.lianzhi.attendance.framework.cache;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 缓存命中统计。
 *
 * <p>统计口径是"本进程内累计值"，重启归零。它存在的意义是让"缓存到底有没有在工作"
 * 变成可验证的事实：健康检查会暴露 hit/miss/error，如果长期只有 miss 没有 hit，
 * 说明缓存没起作用而不是"配了就算用了"。</p>
 */
public class CacheStats {

    private final AtomicLong hit = new AtomicLong();
    private final AtomicLong miss = new AtomicLong();
    private final AtomicLong write = new AtomicLong();
    private final AtomicLong error = new AtomicLong();
    /** Redis 不可用而走直查的次数：正常情况下应长期为 0 */
    private final AtomicLong fallback = new AtomicLong();

    public void recordHit() {
        hit.incrementAndGet();
    }

    public void recordMiss() {
        miss.incrementAndGet();
    }

    public void recordWrite() {
        write.incrementAndGet();
    }

    public void recordError() {
        error.incrementAndGet();
    }

    public void recordFallback() {
        fallback.incrementAndGet();
    }

    public long getHit() {
        return hit.get();
    }

    public long getMiss() {
        return miss.get();
    }

    public long getWrite() {
        return write.get();
    }

    public long getError() {
        return error.get();
    }

    public long getFallback() {
        return fallback.get();
    }

    /** 命中率：没有读取请求时返回 0，不伪造"看起来不错"的默认值。 */
    public double hitRate() {
        long total = hit.get() + miss.get();
        if (total == 0) {
            return 0.0;
        }
        return Math.round(hit.get() * 1000.0 / total) / 10.0;
    }
}
