package com.lianzhi.attendance.knowledge.parse;

import com.lianzhi.attendance.knowledge.entity.KbChunk;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 切片器：固定窗口 + 重叠 + 短块合并。
 *
 * <p>为什么不用"按字符硬切全文"：那样会把跨段落的上下文搅在一起，
 * 来源位置也失去意义。这里先按解析器给出的自然段/页/工作表切，
 * 只对超长段做滑窗，再把过短的相邻块合并，兼顾检索命中和可读性。</p>
 */
@Component
@RequiredArgsConstructor
public class ChunkSplitter {

    private final com.lianzhi.attendance.knowledge.config.KnowledgeProperties properties;

    /**
     * @param documentId 所属文档；由调用方在落库前填入
     */
    public List<KbChunk> split(Long documentId, List<ParsedSection> sections) {
        int size = Math.max(80, properties.getChunkSize());
        int overlap = Math.min(Math.max(0, properties.getChunkOverlap()), size / 2);

        List<KbChunk> pieces = new ArrayList<>();
        for (ParsedSection section : sections) {
            String text = section.getText();
            if (text == null || text.isBlank()) {
                continue;
            }
            if (text.length() <= size) {
                pieces.add(chunk(documentId, section.getSection(), text));
                continue;
            }
            // 超长段：滑窗切分，保留重叠
            int start = 0;
            int part = 1;
            while (start < text.length()) {
                int end = Math.min(start + size, text.length());
                String piece = text.substring(start, end);
                pieces.add(chunk(documentId, section.getSection() + "（片段 " + part + "）", piece));
                if (end >= text.length()) {
                    break;
                }
                start = end - overlap;
                part++;
            }
        }
        return mergeShort(documentId, pieces, size);
    }

    /**
     * 合并过短的相邻块。
     *
     * <p>几十字的碎片命中后给不出完整上下文，答案会显得支离破碎；
     * 但要控制在窗口内，否则又退化成"切了等于没切"。</p>
     */
    private List<KbChunk> mergeShort(Long documentId, List<KbChunk> pieces, int size) {
        List<KbChunk> merged = new ArrayList<>();
        KbChunk pending = null;
        for (KbChunk piece : pieces) {
            if (pending == null) {
                pending = piece;
                continue;
            }
            String joined = pending.getContent() + "\n" + piece.getContent();
            if (joined.length() <= size) {
                pending.setContent(joined);
                pending.setSection(pending.getSection() + " ~ " + piece.getSection());
                continue;
            }
            merged.add(pending);
            pending = piece;
        }
        if (pending != null) {
            merged.add(pending);
        }
        // 合并后重新编号，保证 chunk_index 连续（界面与来源引用都依赖它）
        for (int i = 0; i < merged.size(); i++) {
            KbChunk chunk = merged.get(i);
            chunk.setChunkIndex(i);
            chunk.setDocumentId(documentId);
            chunk.setTokenEstimate(estimateTokens(chunk.getContent()));
        }
        return merged;
    }

    private static KbChunk chunk(Long documentId, String section, String content) {
        KbChunk chunk = new KbChunk();
        chunk.setDocumentId(documentId);
        chunk.setChunkIndex(0);
        chunk.setSection(section);
        chunk.setContent(content);
        chunk.setTokenEstimate(estimateTokens(content));
        return chunk;
    }

    /**
     * 粗略长度估算：中文按 1 字≈1 token，英文按 4 字符≈1 token。
     * 只用于控制上下文长度，不用于计费，够用即可。
     */
    private static int estimateTokens(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        int cjk = 0;
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) >= 0x4E00 && content.charAt(i) <= 0x9FFF) {
                cjk++;
            }
        }
        int others = content.length() - cjk;
        return cjk + (others + 3) / 4;
    }
}
