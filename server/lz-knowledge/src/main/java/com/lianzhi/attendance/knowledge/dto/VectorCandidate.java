package com.lianzhi.attendance.knowledge.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 向量召回候选：带 embedding JSON 的切片。
 *
 * <p>余弦相似度在 Java 内存里算（切片量级为千级，全量候选可控），
 * 所以这里必须把向量原文带回来；算完即弃，不向接口返回。</p>
 */
@Getter
@Setter
public class VectorCandidate {

    private Long chunkId;
    private Long documentId;
    private String documentTitle;
    private String section;
    private String content;
    /** kb_chunk.embedding 原文（JSON 数组字符串） */
    private String embedding;
}
