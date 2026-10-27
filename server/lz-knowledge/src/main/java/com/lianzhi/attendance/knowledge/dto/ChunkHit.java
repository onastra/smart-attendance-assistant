package com.lianzhi.attendance.knowledge.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 一次检索命中的切片。
 *
 * <p>{@code score} 是 MySQL FULLTEXT 的相关度分；{@code excerpt} 是从切片里截出的
 * 上下文片段，用于界面展示来源，不替代切片正文。</p>
 */
@Getter
@Setter
public class ChunkHit {

    private Long chunkId;
    private Long documentId;
    private String documentTitle;
    private String section;
    private String content;
    private String excerpt;
    private Double score;

    /**
     * 命中通道：keyword（全文）/ vector（语义）/ both（两路都中）。
     *
     * <p>展示给前端是为了可核对：纯语义命中的片段不一定包含问题原词，
     * 标出来用户才不会误以为系统"匹配错了词"。</p>
     */
    private String matchType;
}
