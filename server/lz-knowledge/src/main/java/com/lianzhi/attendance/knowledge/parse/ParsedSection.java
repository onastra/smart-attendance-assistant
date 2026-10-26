package com.lianzhi.attendance.knowledge.parse;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 解析器产出的一个"带位置信息的文本块"。
 *
 * <p>{@code section} 的取值随文件类型不同而不同（页码 / 工作表名+行号 / 段落序号），
 * 它最终会写进 {@code kb_chunk.section}，成为问答结果里可核对的来源位置。</p>
 */
@Getter
@AllArgsConstructor
public class ParsedSection {

    private final String section;
    private final String text;
}
