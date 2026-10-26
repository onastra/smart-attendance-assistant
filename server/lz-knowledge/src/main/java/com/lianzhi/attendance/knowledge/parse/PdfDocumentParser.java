package com.lianzhi.attendance.knowledge.parse;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * PDF 文本抽取（PDFBox 2.0.30）。
 *
 * <p><strong>能力边界（如实说明）</strong>：抽取的是 PDF 内嵌的文本流，
 * 对扫描件（页面只有图片没有文字层）拿不到任何内容——这不是解析失败而是文件本身没有文本层，
 * 此时返回空列表，由上层按"无有效内容"处理，不做 OCR，也不谎称解析成功。</p>
 */
@Slf4j
@Component
public class PdfDocumentParser implements DocumentParser {

    @Override
    public Set<String> supportedTypes() {
        return Set.of("PDF");
    }

    @Override
    public List<ParsedSection> parse(InputStream in, int maxChars) {
        List<ParsedSection> sections = new ArrayList<>();
        // startPage/endPage 逐页设置，才能拿到"这段文本来自第几页"
        try (PDDocument document = PDDocument.load(in)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            int total = document.getNumberOfPages();
            int used = 0;
            for (int page = 1; page <= total; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = normalize(stripper.getText(document));
                if (text.isEmpty()) {
                    continue;
                }
                if (used + text.length() > maxChars) {
                    sections.add(new ParsedSection("第 " + page + " 页（已截断）",
                            text.substring(0, Math.max(0, maxChars - used))));
                    break;
                }
                sections.add(new ParsedSection("第 " + page + " 页", text));
                used += text.length();
            }
        } catch (Exception e) {
            // 加密 PDF、损坏 PDF 都会走到这里：抛出让上层把文档置为 FAILED 并写明原因
            throw new IllegalStateException("PDF 解析失败：" + e.getMessage(), e);
        }
        return sections;
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        // PDF 抽取常带大量空行与断行，压缩一下能显著提高切片的信息密度
        return raw.replace("\r", "\n")
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
