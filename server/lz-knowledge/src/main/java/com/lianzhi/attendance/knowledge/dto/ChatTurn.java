package com.lianzhi.attendance.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 一轮对话（传给 LLM 的历史上下文）。
 */
@Getter
@AllArgsConstructor
public class ChatTurn {

    /** user / assistant */
    private final String role;
    private final String content;
}
