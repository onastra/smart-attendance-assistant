package com.lianzhi.attendance.knowledge.parse;

import java.io.InputStream;
import java.util.List;
import java.util.Set;

/**
 * 文档解析器。
 *
 * <p>只负责"把文件变成带位置的文本块"，不负责切分、不负责落库——
 * 这样每个解析器都能单独用一份样例文件做单测（需求 TC-F-07）。</p>
 */
public interface DocumentParser {

    /** 该解析器支持的 fileType 集合（大写） */
    Set<String> supportedTypes();

    /**
     * @param in     文件流，由调用方负责关闭
     * @param maxChars 单文档最大抽取字符数，防止超大文件把内存打满
     */
    List<ParsedSection> parse(InputStream in, int maxChars);
}
