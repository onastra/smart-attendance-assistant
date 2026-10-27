package com.lianzhi.attendance.knowledge.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 问答响应。
 *
 * <p>三个字段是这套设计的诚实性所在，任何情况下都不能省：</p>
 * <ul>
 *   <li>{@code mode}：{@code retrieval-only} 表示没有模型参与，答案只是检索片段的整理；
 *       {@code llm-augmented} 表示模型在检索结果基础上做了生成。</li>
 *   <li>{@code grounded}：false 表示检索没有足够依据，此时 {@code answer} 是一句
 *       "未找到足够依据"，而不是模型凭印象编的一段话。</li>
 *   <li>{@code sources}：来源引用，用户可据此核对。</li>
 * </ul>
 */
@Getter
@Setter
public class AskResponse {

    private String question;
    private String answer;
    /** retrieval-only / llm-augmented */
    private String mode;
    /** 答案是否有检索依据支撑 */
    private boolean grounded;
    /** 未命中时的原因码：NO_HIT / BELOW_THRESHOLD / NO_DOCUMENT */
    private String reason;
    /** 生成模型名；retrieval-only 时为 null */
    private String model;
    private Double topScore;
    private Integer latencyMs;
    private List<ChunkHit> sources;
    /** 无 Key 时的提示文案，前端据此展示缺失提示条 */
    private String llmNotice;

    /** 本轮问答归属的会话 ID */
    private Long conversationId;

    /** 助手消息 ID（kb_message 主键） */
    private Long messageId;
}
