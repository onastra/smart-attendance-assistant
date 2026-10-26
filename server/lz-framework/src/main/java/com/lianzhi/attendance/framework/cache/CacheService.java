package com.lianzhi.attendance.framework.cache;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 开关化缓存。
 *
 * <p>设计前提：<b>缓存永远是可选的加速手段，不是数据来源</b>。因此
 * <ul>
 *   <li>开关关闭（{@code lz.cache.enabled=false}）时走 {@link NoopCacheService}，
 *       每次都执行 loader，接口结果不变，只是没有加速；</li>
 *   <li>开关打开但 Redis 连不上时，单次请求内降级为直查并记 WARN，
 *       连续失败后进入短时熔断，避免每个请求都在等连接超时；</li>
 *   <li>任何缓存异常都不允许冒泡成业务异常——缓存出错最多是慢，不能是错。</li>
 * </ul>
 */
public interface CacheService {

    /** 缓存模式：{@code redis} 或 {@code none}。 */
    String mode();

    /** 当前是否真的可用（redis 模式下会真实 PING 一次）。 */
    boolean available();

    /**
     * 读取缓存。
     *
     * @return 命中则返回反序列化后的对象；未命中或缓存不可用时返回 null
     */
    <T> T get(String key, Class<T> type);

    /** 写入缓存；value 为 null 时不做任何事。 */
    void put(String key, Object value, Duration ttl);

    /** 删除单个键。 */
    void evict(String key);

    /** 按前缀删除（用于"某类数据变更后整体失效"）。 */
    void evictByPrefix(String prefix);

    /**
     * 读缓存，未命中则加载并回填。
     *
     * <p>这是业务侧推荐使用的入口：调用方不需要关心降级细节，
     * 也拿不到"假装命中"的结果——不可用时一定返回 loader 的真实结果。</p>
     */
    default <T> T getOrLoad(String key, Class<T> type, Duration ttl, Supplier<T> loader) {
        T cached = get(key, type);
        if (cached != null) {
            return cached;
        }
        T loaded = loader.get();
        if (loaded != null) {
            put(key, loaded, ttl);
        }
        return loaded;
    }

    /** 本进程内的命中统计。 */
    CacheStats stats();
}
