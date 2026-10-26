package com.lianzhi.attendance.knowledge.parse;

import com.lianzhi.attendance.knowledge.config.KnowledgeProperties;
import com.lianzhi.attendance.knowledge.entity.KbChunk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 切片器的单元测试。
 *
 * <p>切片质量直接决定检索命不命中、以及命中后给出的上下文完不完整：
 * 切太碎，几十字的碎片答不出完整语义；切太大，又退化成"切了等于没切"。
 * 这里把三条规则（窗口、重叠、短块合并）分别断言。</p>
 */
class ChunkSplitterTest {

    private static ChunkSplitter splitter(int chunkSize, int overlap) {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.setChunkSize(chunkSize);
        properties.setChunkOverlap(overlap);
        return new ChunkSplitter(properties);
    }

    private static String repeat(String unit, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) {
            sb.append(unit);
        }
        return sb.toString();
    }

    @Test
    @DisplayName("短于窗口的段落单独成块，内容完整不截断")
    void shortSectionBecomesSingleChunk() {
        List<KbChunk> chunks = splitter(300, 60)
                .split(1L, Collections.singletonList(new ParsedSection("第 1 段", "考勤规则说明文本。")));

        assertEquals(1, chunks.size());
        assertEquals("考勤规则说明文本。", chunks.get(0).getContent());
        assertEquals("第 1 段", chunks.get(0).getSection());
        assertEquals(1L, chunks.get(0).getDocumentId());
    }

    @Test
    @DisplayName("超长段落按窗口滑切，每块长度不超过窗口")
    void longSectionIsSplitWithinWindow() {
        String longText = repeat("考勤管理制度条款内容。", 100); // 1000 字
        List<KbChunk> chunks = splitter(300, 60)
                .split(1L, Collections.singletonList(new ParsedSection("第 2 页", longText)));

        assertTrue(chunks.size() > 1, "超长段落必须切成多块");
        for (KbChunk chunk : chunks) {
            assertTrue(chunk.getContent().length() <= 300,
                    "单块内容不得超过窗口 300，实际：" + chunk.getContent().length());
        }
        assertEquals(longText.length(),
                chunks.stream().mapToInt(c -> c.getContent().length()).sum() - overlapTotal(chunks, 60),
                "切成的内容加上重叠应能覆盖原文长度");
    }

    @Test
    @DisplayName("相邻切片保留指定的重叠字符，避免一句话被切断后两边都检索不到")
    void adjacentChunksKeepOverlap() {
        String text = repeat("甲乙丙丁戊己庚辛壬癸", 80); // 800 字
        List<KbChunk> chunks = splitter(300, 60)
                .split(1L, Collections.singletonList(new ParsedSection("第 1 页", text)));

        assertTrue(chunks.size() >= 3, "需要至少三块才能验证相邻重叠，实际：" + chunks.size());
        KbChunk first = chunks.get(0);
        KbChunk second = chunks.get(1);
        String tailOfFirst = first.getContent().substring(first.getContent().length() - 60);
        assertTrue(second.getContent().startsWith(tailOfFirst),
                "第二块的开头必须是第一块结尾的 60 字重叠");
    }

    @Test
    @DisplayName("过短的相邻块会被合并，避免几十字的碎片")
    void shortChunksAreMerged() {
        List<ParsedSection> sections = Arrays.asList(
                new ParsedSection("第 1 段", repeat("短句一。", 5)),
                new ParsedSection("第 2 段", repeat("短句二。", 5)),
                new ParsedSection("第 3 段", repeat("短句三。", 5)));

        List<KbChunk> chunks = splitter(300, 60).split(1L, sections);

        assertEquals(1, chunks.size(), "三段短文本合计不足一个窗口，应合并为一块");
        assertTrue(chunks.get(0).getContent().contains("短句一"));
        assertTrue(chunks.get(0).getContent().contains("短句三"));
        assertTrue(chunks.get(0).getSection().contains("~"), "合并后的 section 应体现合并来源");
    }

    @Test
    @DisplayName("合并后 chunk_index 必须从 0 连续递增（界面与来源引用依赖它）")
    void chunkIndexIsContinuousAfterMerge() {
        List<ParsedSection> sections = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            sections.add(new ParsedSection("第 " + i + " 段", repeat("内容" + i + "。", 40)));
        }

        List<KbChunk> chunks = splitter(300, 60).split(1L, sections);

        for (int i = 0; i < chunks.size(); i++) {
            assertEquals(i, chunks.get(i).getChunkIndex(), "索引必须连续，第 " + i + " 块不符");
            assertEquals(1L, chunks.get(i).getDocumentId());
        }
    }

    @Test
    @DisplayName("空白段落被跳过，不产生空块")
    void blankSectionsAreSkipped() {
        List<ParsedSection> sections = Arrays.asList(
                new ParsedSection("第 1 段", "   "),
                new ParsedSection("第 2 段", null),
                new ParsedSection("第 3 段", "有效内容"));

        List<KbChunk> chunks = splitter(300, 60).split(1L, sections);

        assertEquals(1, chunks.size());
        assertEquals("有效内容", chunks.get(0).getContent());
        assertFalse(chunks.get(0).getContent().isBlank());
    }

    @Test
    @DisplayName("每块都带正的 token 估算值，用于控制送入模型的上下文长度")
    void tokenEstimateIsPositive() {
        List<KbChunk> chunks = splitter(100, 20)
                .split(1L, Collections.singletonList(new ParsedSection("第 1 段", repeat("知识库文本。", 50))));

        assertFalse(chunks.isEmpty());
        for (KbChunk chunk : chunks) {
            assertTrue(chunk.getTokenEstimate() > 0, "token 估算必须为正");
        }
    }

    @Test
    @DisplayName("窗口取值被下限保护：配置成 1 也按 80 生效，且不会死循环")
    void tinyWindowIsProtectedByLowerBound() {
        // size 经 Math.max(80, 1) 修正为 80，overlap 为 0，200 字应切成 3 块且互不重叠
        List<KbChunk> chunks = splitter(1, 0)
                .split(1L, Collections.singletonList(new ParsedSection("第 1 段", repeat("字", 200))));

        assertEquals(3, chunks.size(), "实际窗口被保护为 80，200 字应切成 3 块");
        assertTrue(chunks.get(0).getContent().length() <= 80);
        assertEquals(repeat("字", 200),
                chunks.stream().map(KbChunk::getContent).collect(Collectors.joining("")),
                "overlap=0 时各块首尾相接应能还原原文");
    }

    /**
     * 计算除首块外各块与前一块的重复长度（用于还原原文总长）。
     */
    private static int overlapTotal(List<KbChunk> chunks, int overlap) {
        int total = 0;
        for (int i = 1; i < chunks.size(); i++) {
            total += overlap;
        }
        return total;
    }

    @Test
    @DisplayName("空输入返回空列表，不抛异常")
    void emptyInputReturnsEmptyList() {
        assertTrue(splitter(300, 60).split(1L, Collections.emptyList()).isEmpty());
    }

    @Test
    @DisplayName("所有块拼回去应能覆盖原文全部字符（不丢字）")
    void noCharacterIsLost() {
        String text = repeat("连贯正文。", 60);
        List<KbChunk> chunks = splitter(200, 50)
                .split(1L, Collections.singletonList(new ParsedSection("第 1 页", text)));

        String covered = chunks.stream().map(KbChunk::getContent).collect(Collectors.joining(""));
        assertTrue(covered.contains(text.substring(0, 200)), "首段原文必须完整出现");
        assertTrue(covered.contains(text.substring(text.length() - 100)), "尾段原文必须完整出现");
    }
}
