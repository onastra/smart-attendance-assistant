package com.lianzhi.attendance.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 提问入参。
 */
@Getter
@Setter
public class AskRequest {

    @NotBlank(message = "问题不能为空")
    @Size(max = 500, message = "问题最长 500 字")
    private String question;

    /** 可选：限定在某个文档内检索 */
    private Long documentId;

    /** 可选：返回的最大来源条数 */
    private Integer topK;

    /** 可选：会话 ID。为空时自动新建会话；非空时校验归属并携带历史上下文 */
    private Long conversationId;
}
