package com.lianzhi.attendance.knowledge.embedding;

import java.util.List;

/**
 * 向量嵌入模型抽象：把文本变成浮点向量。
 *
 * <p>与 {@code LlmClient} 同级的可插拔设计：可整体缺失（缺失时检索退化为纯全文匹配，
 * 不影响问答主链路），也可替换为任意 OpenAI 兼容的 /embeddings 服务。</p>
 */
public interface EmbeddingClient {

    /** 是否可用（配置齐全才算可用，缺配置只允许降级，不允许启动失败） */
    boolean available();

    /** 模型名，用于落库标记与状态展示 */
    String name();

    /** 不可用时的原因说明；可用时返回 null */
    String unavailableReason();

    /**
     * 批量向量化。
     *
     * @return 与输入等长、同顺序的向量列表
     */
    List<float[]> embed(List<String> texts);

    /** 向量维度；未知时返回 0 */
    default int dimension() {
        return 0;
    }
}
