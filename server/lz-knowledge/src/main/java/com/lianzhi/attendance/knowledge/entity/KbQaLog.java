package com.lianzhi.attendance.knowledge.entity;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 问答日志。
 *
 * <p><strong>刻意不记录答案正文</strong>：答案里可能含文档原文片段、模型输出，
 * 一旦落库就等于把受限文档的内容复制了一份到日志表，绕过可见范围控制；
 * 同时也不记录任何密钥。日志只保留"问了什么、命中几段、最高分、什么模式、耗时"。</p>
 */
@Getter
@Setter
public class KbQaLog {

    private Long id;
    private Long userId;
    private String question;
    private Integer hitCount;
    private BigDecimal topScore;
    /** retrieval-only / llm-augmented */
    private String mode;
    private Integer latencyMs;
    private LocalDateTime createdAt;

    /** 冗余展示字段 */
    private String username;
}
