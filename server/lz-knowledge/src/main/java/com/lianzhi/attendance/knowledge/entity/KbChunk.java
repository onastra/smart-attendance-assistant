package com.lianzhi.attendance.knowledge.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 知识库切片。
 *
 * <p>{@code section} 记录切片在原文中的位置（页码 / 工作表名+行号 / 段落序号），
 * 问答结果要靠它做来源引用——只给文档名不给位置，用户无法核对答案是否真的来自该文档。</p>
 */
@Getter
@Setter
public class KbChunk {

    private Long id;
    private Long documentId;
    private Integer chunkIndex;
    private String section;
    private String content;
    /** 粗略长度估算，用于回答时控制上下文长度 */
    private Integer tokenEstimate;
    private LocalDateTime createdAt;
}
