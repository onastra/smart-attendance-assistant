package com.lianzhi.attendance.knowledge.mapper;

import com.lianzhi.attendance.knowledge.dto.ChunkHit;
import com.lianzhi.attendance.knowledge.dto.VectorCandidate;
import com.lianzhi.attendance.knowledge.entity.KbChunk;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 知识库切片持久化与全文检索。
 *
 * <p>检索入口有两个，原因见 {@link #searchByFullText} 与 {@link #searchByLike} 的注释。</p>
 */
public interface KbChunkMapper {

    int insertBatch(@Param("chunks") List<KbChunk> chunks);

    int deleteByDocumentId(@Param("documentId") Long documentId);

    List<KbChunk> selectByDocumentId(@Param("documentId") Long documentId);

    /**
     * ngram 全文检索。
     *
     * <p>MySQL 5.7 的 ngram 解析器按 {@code ngram_token_size=2} 切词，
     * 因此<strong>单字查询永远不会命中</strong>——这不是 bug 而是配置结果，
     * 调用方需要在关键词长度小于 2 时走 {@link #searchByLike}。</p>
     *
     * <p>可见范围过滤写在 WHERE 里而不是返回后再过滤：先取 TopN 再过滤，
     * 会出现"有权看的文档因为排序靠后被截断，反而返回了无权看的"这类越权泄漏。</p>
     */
    List<ChunkHit> searchByFullText(@Param("keyword") String keyword,
                                    @Param("deptId") Long deptId,
                                    @Param("userId") Long userId,
                                    @Param("documentId") Long documentId,
                                    @Param("minScore") Double minScore,
                                    @Param("limit") int limit);

    /**
     * 子串兜底检索：只用于单字查询等 ngram 无法命中的场景。
     *
     * <p>LIKE '%x%' 不能走索引，切片量大了会慢；这里限制返回条数，
     * 并在响应里如实说明命中方式，不伪装成语义检索。</p>
     */
    List<ChunkHit> searchByLike(@Param("keyword") String keyword,
                                @Param("deptId") Long deptId,
                                @Param("userId") Long userId,
                                @Param("documentId") Long documentId,
                                @Param("limit") int limit);

    /** 回写单条切片的向量（索引/重建时调用） */
    int updateEmbedding(@Param("id") Long id,
                        @Param("embedding") String embedding,
                        @Param("model") String model);

    /**
     * 取向量召回候选集：已生成向量且可见的切片。
     *
     * <p>可见范围过滤同样写在 WHERE 里，与全文检索同口径。候选数由 limit 封顶，
     * 相似度计算在 Java 内存完成。</p>
     */
    List<VectorCandidate> selectVectorCandidates(@Param("deptId") Long deptId,
                                                 @Param("userId") Long userId,
                                                 @Param("documentId") Long documentId,
                                                 @Param("limit") int limit);
}
