package com.lianzhi.attendance.knowledge.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 知识库配置项。
 *
 * <p>全部走 {@code lz.kb.*}，与环境解耦：换机器只改环境变量，不改代码。</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "lz.kb")
public class KnowledgeProperties {

    /**
     * 单个切片的目标字符数。
     *
     * <p>取 300 而不是常见的 500：中文信息密度高，500 字往往塞进了两三个小节，
     * 命中后给用户的上下文反而太散。</p>
     */
    private int chunkSize = 300;

    /** 相邻切片的重叠字符数，避免一句话被切断后两边都检索不到 */
    private int chunkOverlap = 60;

    /** 单文档最大抽取字符数，防止超大文件吃满内存 */
    private int maxCharsPerDocument = 200_000;

    /** 单文件最大字节数 */
    private long maxFileSizeBytes = 10 * 1024 * 1024L;

    /**
     * 全文检索相关度下限。
     *
     * <p>低于该分视为"没有真正的语义命中"，直接返回"未找到足够依据"而不是硬凑一段。
     * ngram 的分数绝对值偏小，默认取 0.05 并按实际语料校准过。</p>
     */
    private double minScore = 0.05D;

    /** 一次问答最多带回几条来源 */
    private int topK = 5;

    /** 来源片段展示长度 */
    private int excerptLength = 120;

    /** 支持的扩展名白名单（小写） */
    private String allowedExtensions = "pdf,docx,xlsx,pptx,doc,xls,ppt,txt,md";
}
