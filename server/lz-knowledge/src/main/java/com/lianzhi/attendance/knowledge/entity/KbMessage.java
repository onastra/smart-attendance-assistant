package com.lianzhi.attendance.knowledge.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 会话消息。
 *
 * <p>与 kb_qa_log 的分工：qa_log 是审计日志（只记问题不记答案，防越权泄漏）；
 * 本表是用户自己的对话历史——答案本来就是答给该用户本人的，且接口层强制
 * 只允许本人读取。sources 存来源引用快照，用户回看历史时仍能核对出处。</p>
 */
@Getter
@Setter
public class KbMessage {

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    private Long id;
    private Long conversationId;
    /** user / assistant */
    private String role;
    private String content;
    /** assistant 消息的来源引用快照（JSON 数组字符串） */
    private String sources;
    /** retrieval-only / llm-augmented */
    private String mode;
    private LocalDateTime createdAt;
}
