package com.lianzhi.attendance.knowledge.parse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 纯文本解析（TXT / MD）。
 *
 * <p>Markdown 不做语法剥离：标题、列表符号本身就是章节结构线索，
 * 去掉反而会让"来源位置"更难定位。只做空行压缩。</p>
 */
@Slf4j
@Component
public class TextDocumentParser implements DocumentParser {

    @Override
    public Set<String> supportedTypes() {
        return Set.of("TXT", "MD");
    }

    @Override
    public List<ParsedSection> parse(InputStream in, int maxChars) {
        List<ParsedSection> sections = new ArrayList<>();
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder block = new StringBuilder();
            String line;
            int blockIndex = 0;
            int used = 0;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    if (block.length() > 0) {
                        blockIndex++;
                        String text = block.toString().trim();
                        if (used + text.length() > maxChars) {
                            sections.add(new ParsedSection("第 " + blockIndex + " 段（已截断）",
                                    text.substring(0, Math.max(0, maxChars - used))));
                            return sections;
                        }
                        sections.add(new ParsedSection("第 " + blockIndex + " 段", text));
                        used += text.length();
                        block.setLength(0);
                    }
                    continue;
                }
                block.append(line).append('\n');
            }
            if (block.length() > 0) {
                blockIndex++;
                String text = block.toString().trim();
                if (used + text.length() <= maxChars) {
                    sections.add(new ParsedSection("第 " + blockIndex + " 段", text));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("文本解析失败：" + e.getMessage(), e);
        }
        return sections;
    }
}
