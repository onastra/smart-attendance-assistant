package com.lianzhi.attendance.knowledge.llm;

import com.lianzhi.attendance.knowledge.dto.ChatTurn;
import com.lianzhi.attendance.knowledge.dto.ChunkHit;

import java.util.List;
import java.util.function.Consumer;

/**
 * 生成模型的抽象。
 *
 * <p>存在的意义是把"检索"和"生成"彻底分开：检索永远是本系统自己做（MySQL 全文检索
 * + 向量混合召回），模型只负责在检索结果之上做措辞整理。这样即使完全没有模型，
 * 问答依然可用——只是 mode 会变成 retrieval-only，而不是直接不可用。</p>
 */
public interface LlmClient {

    /**
     * 该实现当前是否真的可用。
     *
     * <p>与"是否被 Spring 装配"不是一回事：装配了但缺 Key / 缺地址，这里仍返回 false。</p>
     */
    boolean available();

    /** 实现名，会写进响应的 model 字段，便于前端与日志判断当前用的是谁 */
    String name();

    /** 不可用时给用户的说明，可用时为 null */
    String unavailableReason();

    /**
     * 基于检索结果生成回答。
     *
     * <p>契约：必须只使用传入的 sources 作为依据，不得补充自身知识。
     * 这条约束会写进提示词，也是"不编造"在代码层面的落点。</p>
     */
    String generate(String question, List<ChunkHit> sources);

    /**
     * 带会话历史的生成。默认忽略历史，保持与旧实现的兼容；
     * 支持多轮对话的实现应覆写此方法。
     */
    default String generate(String question, List<ChunkHit> sources, List<ChatTurn> history) {
        return generate(question, sources);
    }

    /** 是否支持真正的流式输出（false 时由默认实现把整段答案一次性吐出） */
    default boolean supportsStreaming() {
        return false;
    }

    /**
     * 流式生成：每拿到一段增量就回调一次。
     *
     * <p>默认实现退化为一次性回调整段答案，保证不支持流式的模型也能接入同一条链路。</p>
     */
    default void generateStream(String question, List<ChunkHit> sources,
                                List<ChatTurn> history, Consumer<String> deltaConsumer) {
        deltaConsumer.accept(generate(question, sources, history));
    }
}

