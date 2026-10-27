package com.lianzhi.attendance.knowledge.retrieve;

import com.lianzhi.attendance.framework.auth.AuthUser;
import com.lianzhi.attendance.knowledge.config.KnowledgeProperties;
import com.lianzhi.attendance.knowledge.dto.ChunkHit;
import com.lianzhi.attendance.knowledge.dto.VectorCandidate;
import com.lianzhi.attendance.knowledge.embedding.EmbeddingClient;
import com.lianzhi.attendance.knowledge.embedding.VectorCodec;
import com.lianzhi.attendance.knowledge.mapper.KbChunkMapper;
import com.lianzhi.attendance.knowledge.mapper.KbDocumentMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 检索器：把用户问题变成"有权阅读的、相关度达标的切片"。
 *
 * <p>三条硬性约束：</p>
 * <ol>
 *   <li><strong>可见范围在 SQL 里过滤</strong>，不是查完再过滤。否则先按相关度取 TopN，
 *       无权文档可能因为分高被返回，等于越权泄漏。关键词通道与向量通道同口径。</li>
 *   <li><strong>低于阈值直接不返回</strong>。检索不达标时报"未找到足够依据"，
 *       而不是把勉强沾边的片段硬塞给用户（需求 IDX-F6）。</li>
 *   <li><strong>语义检索是增强不是依赖</strong>：向量服务缺失或调用失败时，
 *       自动退化为纯全文检索，主链路不受影响。</li>
 * </ol>
 *
 * <p>两路召回用 RRF（Reciprocal Rank Fusion）融合：score = Σ 1/(k + rank)。
 * 两路分数量纲完全不同（全文是自然语言相关度，向量是余弦相似度），不能直接相加；
 * RRF 只用名次不用分数，是该场景的标准做法。</p>
 */
@Slf4j
@Component
public class Retriever {

    /**
     * ngram_token_size=2：单字查询在全文索引里永远命中不了，
     * 这不是故障而是索引配置的结果，必须显式兜底。
     */
    private static final int NGRAM_MIN_TERM = 2;

    /** RRF 平滑常数，业界惯用 60 */
    private static final int RRF_K = 60;

    /** 每路召回的候选倍数：融合前多取一些，避免单路高分项挤掉双路命中项 */
    private static final int CANDIDATE_FACTOR = 3;

    private final KbChunkMapper chunkMapper;
    private final KbDocumentMapper documentMapper;
    private final KnowledgeProperties properties;
    private final List<EmbeddingClient> embeddingClients;

    public Retriever(KbChunkMapper chunkMapper,
                     KbDocumentMapper documentMapper,
                     KnowledgeProperties properties,
                     List<EmbeddingClient> embeddingClients) {
        this.chunkMapper = chunkMapper;
        this.documentMapper = documentMapper;
        this.properties = properties;
        this.embeddingClients = embeddingClients == null ? List.of() : embeddingClients;
    }

    public List<ChunkHit> retrieve(String question, AuthUser user, Long documentId, Integer topK) {
        String keyword = normalize(question);
        Long deptId = deptIdOf(user);
        Long userId = user == null ? null : user.getUserId();
        int limit = topK == null || topK <= 0 ? properties.getTopK() : Math.min(topK, 20);
        int candidateLimit = limit * CANDIDATE_FACTOR;

        List<ChunkHit> keywordHits = keywordRetrieve(keyword, deptId, userId, documentId, candidateLimit);
        List<ChunkHit> vectorHits = vectorRetrieve(question, deptId, userId, documentId, candidateLimit);

        List<ChunkHit> fused = fuse(keywordHits, vectorHits, limit);
        fused.forEach(this::fillExcerpt);
        return fused;
    }

    // ---------------- 关键词通道 ----------------

    private List<ChunkHit> keywordRetrieve(String keyword, Long deptId, Long userId,
                                           Long documentId, int limit) {
        if (keyword.isEmpty()) {
            return List.of();
        }
        if (keyword.length() >= NGRAM_MIN_TERM) {
            return chunkMapper.searchByFullText(
                    keyword, deptId, userId, documentId, properties.getMinScore(), limit);
        }
        // 单字查询：退化为子串匹配，不伪装成语义检索
        return chunkMapper.searchByLike(keyword, deptId, userId, documentId, limit);
    }

    // ---------------- 向量通道 ----------------

    private List<ChunkHit> vectorRetrieve(String question, Long deptId, Long userId,
                                          Long documentId, int limit) {
        EmbeddingClient client = pickEmbedding();
        if (client == null || question == null || question.isBlank()) {
            return List.of();
        }
        float[] queryVector;
        try {
            queryVector = client.embed(List.of(question)).get(0);
        } catch (Exception e) {
            // 向量服务抖动只影响语义通道，关键词通道照常工作
            log.warn("[KB] 问题向量化失败，本轮退化为纯全文检索：{}", e.getMessage());
            return List.of();
        }
        List<VectorCandidate> candidates = chunkMapper.selectVectorCandidates(
                deptId, userId, documentId, properties.getVectorCandidateLimit());
        List<ChunkHit> hits = new ArrayList<>();
        for (VectorCandidate c : candidates) {
            double score = VectorCodec.cosine(queryVector, VectorCodec.fromJson(c.getEmbedding()));
            if (score < properties.getVectorMinScore()) {
                continue;
            }
            ChunkHit hit = new ChunkHit();
            hit.setChunkId(c.getChunkId());
            hit.setDocumentId(c.getDocumentId());
            hit.setDocumentTitle(c.getDocumentTitle());
            hit.setSection(c.getSection());
            hit.setContent(c.getContent());
            hit.setScore(score);
            hits.add(hit);
        }
        hits.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return hits.size() > limit ? hits.subList(0, limit) : hits;
    }

    // ---------------- RRF 融合 ----------------

    private List<ChunkHit> fuse(List<ChunkHit> keywordHits, List<ChunkHit> vectorHits, int limit) {
        if (vectorHits.isEmpty()) {
            keywordHits.forEach(h -> h.setMatchType("keyword"));
            return truncate(keywordHits, limit);
        }
        if (keywordHits.isEmpty()) {
            vectorHits.forEach(h -> h.setMatchType("vector"));
            return truncate(vectorHits, limit);
        }
        Map<Long, ChunkHit> byId = new HashMap<>();
        Map<Long, Double> fusedScore = new HashMap<>();
        for (int rank = 0; rank < keywordHits.size(); rank++) {
            ChunkHit hit = keywordHits.get(rank);
            byId.put(hit.getChunkId(), hit);
            fusedScore.merge(hit.getChunkId(), 1D / (RRF_K + rank + 1), Double::sum);
        }
        for (int rank = 0; rank < vectorHits.size(); rank++) {
            ChunkHit hit = vectorHits.get(rank);
            byId.putIfAbsent(hit.getChunkId(), hit);
            fusedScore.merge(hit.getChunkId(), 1D / (RRF_K + rank + 1), Double::sum);
        }
        List<ChunkHit> fused = new ArrayList<>(byId.values());
        fused.sort((a, b) -> Double.compare(
                fusedScore.get(b.getChunkId()), fusedScore.get(a.getChunkId())));
        fused.forEach(hit -> {
            Long id = hit.getChunkId();
            boolean kw = contains(keywordHits, id);
            boolean vec = contains(vectorHits, id);
            hit.setMatchType(kw && vec ? "both" : kw ? "keyword" : "vector");
            hit.setScore(fusedScore.get(id));
        });
        return truncate(fused, limit);
    }

    private static boolean contains(List<ChunkHit> hits, Long chunkId) {
        for (ChunkHit h : hits) {
            if (h.getChunkId().equals(chunkId)) {
                return true;
            }
        }
        return false;
    }

    private static List<ChunkHit> truncate(List<ChunkHit> hits, int limit) {
        return hits.size() > limit ? new ArrayList<>(hits.subList(0, limit)) : hits;
    }

    /** 取第一个可用的向量模型实现；一个都没装配时返回 null（纯全文模式） */
    private EmbeddingClient pickEmbedding() {
        for (EmbeddingClient client : embeddingClients) {
            if (client.available()) {
                return client;
            }
        }
        return null;
    }

    /** 语义检索是否已启用（供状态接口展示） */
    public boolean vectorEnabled() {
        return pickEmbedding() != null;
    }

    private Long deptIdOf(AuthUser user) {
        if (user == null || user.getEmployeeId() == null) {
            return null;
        }
        return documentMapper.selectEmployeeDeptId(user.getEmployeeId());
    }

    private void fillExcerpt(ChunkHit hit) {
        String content = hit.getContent();
        if (content == null) {
            hit.setExcerpt("");
            return;
        }
        int len = Math.max(20, properties.getExcerptLength());
        hit.setExcerpt(content.length() <= len ? content : content.substring(0, len) + "…");
    }

    /**
     * 去掉标点与多余空白。
     *
     * <p>中文句子里的逗号句号会被 ngram 当作有效字符参与切词，拖低相关度。</p>
     */
    private static String normalize(String question) {
        if (question == null) {
            return "";
        }
        return question.replaceAll("[\\p{P}\\p{S}\\s]+", " ").trim();
    }
}
