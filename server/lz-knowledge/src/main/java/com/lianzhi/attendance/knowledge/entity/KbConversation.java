package com.lianzhi.attendance.knowledge.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 知识库多轮会话。只允许归属用户本人查询与删除。
 */
@Getter
@Setter
public class KbConversation {

    private Long id;
    private Long userId;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
