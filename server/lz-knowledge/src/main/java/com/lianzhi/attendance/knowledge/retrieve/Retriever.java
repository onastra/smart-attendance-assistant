package com.lianzhi.attendance.knowledge.retrieve;

import com.lianzhi.attendance.framework.auth.AuthUser;
import com.lianzhi.attendance.knowledge.config.KnowledgeProperties;
import com.lianzhi.attendance.knowledge.dto.ChunkHit;
import com.lianzhi.attendance.knowledge.mapper.KbChunkMapper;
import com.lianzhi.attendance.knowledge.mapper.KbDocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 检索器：把用户问题变成"有权阅读的、相关度达标的切片"。
 *
 * <p>两条硬性约束：</p>
 * <ol>
 *   <li><strong>可见范围在 SQL 里过滤</strong>，不是查完再过滤。否则先按相关度取 TopN，
 *       无权文档可能因为分高被返回，等于越权泄漏。</li>
 *   <li><strong>低于阈值直接不返回</strong>。检索不达标时报"未找到足够依据"，
 *       而不是把勉强沾边的片段硬塞给用户（需求 IDX-F6）。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Retriever {

    /**
     * ngram_token_size=2：单字查询在全文索引里永远命中不了，
     * 这不是故障而是索引配置的结果，必须显式兜底。
     */
    private static final int NGRAM_MIN_TERM = 2;

    private final KbChunkMapper chunkMapper;
    private final KbDocumentMapper documentMapper;
    private final KnowledgeProperties properties;

    public List<ChunkHit> retrieve(String question, AuthUser user, Long documentId, Integer topK) {
        String keyword = normalize(question);
        if (keyword.isEmpty()) {
            return List.of();
        }
        Long deptId = deptIdOf(user);
        Long userId = user == null ? null : user.getUserId();
        int limit = topK == null || topK <= 0 ? properties.getTopK() : Math.min(topK, 20);

        List<ChunkHit> hits;
        if (keyword.length() >= NGRAM_MIN_TERM) {
            hits = chunkMapper.searchByFullText(
                    keyword, deptId, userId, documentId, properties.getMinScore(), limit);
        } else {
            // 单字查询：退化为子串匹配，并在 section 上标注命中方式，不伪装成语义检索
            hits = chunkMapper.searchByLike(keyword, deptId, userId, documentId, limit);
        }
        hits.forEach(this::fillExcerpt);
        return hits;
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
