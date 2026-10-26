package com.lianzhi.attendance.knowledge.llm;

import com.lianzhi.attendance.knowledge.dto.ChunkHit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 默认实现：没有外部模型。
 *
 * <p>它<strong>不是"假装有个模型"</strong>，而是明确返回不可用，
 * 由编排层据此把问答降级为纯检索并给出提示。这也是需求 IDX-F9 要求的默认行为。</p>
 *
 * <p>排序最低：编排层取"第一个可用的实现"，只有当其它实现都不可用时才会落到它。</p>
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class NoopLlmClient implements LlmClient {

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public String name() {
        return "none";
    }

    @Override
    public String unavailableReason() {
        return "未配置大模型服务（lz.llm.enabled=false 或缺 api-key），"
                + "当前为检索模式：只返回命中的原文片段，不做生成，也不会编造答案。";
    }

    @Override
    public String generate(String question, List<ChunkHit> sources) {
        throw new UnsupportedOperationException("NoopLlmClient 不提供生成能力，编排层应先检查 available()");
    }
}
