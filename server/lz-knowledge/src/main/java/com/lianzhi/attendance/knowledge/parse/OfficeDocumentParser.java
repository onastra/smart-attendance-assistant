package com.lianzhi.attendance.knowledge.parse;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Office 文档解析（POI 5.2.5）。
 *
 * <p>覆盖 docx / xlsx / pptx 三件套，以及旧版 doc / xls / ppt（poi-scratchpad）。
 * 表格与幻灯片里的文字都抽取——企业制度文档大量内容在表格里，只抽段落会漏掉关键信息。</p>
 *
 * <p>不抽取图片、批注、页眉页脚：图片需要 OCR（本项目范围外），
 * 批注与页眉页脚混进正文会污染检索结果。</p>
 */
@Slf4j
@Component
public class OfficeDocumentParser implements DocumentParser {

    private static final Set<String> TYPES =
            Set.of("DOCX", "XLSX", "PPTX", "DOC", "XLS", "PPT");

    @Override
    public Set<String> supportedTypes() {
        return TYPES;
    }

    @Override
    public List<ParsedSection> parse(InputStream in, int maxChars) {
        // 具体格式由调用方通过 parseTyped 传入；这里保留通用入口以应对只知扩展名的场景
        throw new UnsupportedOperationException("Office 解析必须指定具体格式，见 parseTyped");
    }

    /**
     * 按具体格式解析。
     *
     * <p>拆成独立方法而不是在 parse 里 switch：InputStream 只能消费一次，
     * 一旦猜错格式就再也读不回来，必须由调用方明确告知格式。</p>
     */
    public List<ParsedSection> parseTyped(InputStream in, String fileType, int maxChars) {
        try {
            return switch (fileType) {
                case "DOCX" -> parseDocx(in, maxChars);
                case "XLSX" -> parseWorkbook(() -> new XSSFWorkbook(in), maxChars);
                case "PPTX" -> parsePptx(in, maxChars);
                case "DOC" -> parseDoc(in, maxChars);
                case "XLS" -> parseWorkbook(() -> new HSSFWorkbook(in), maxChars);
                case "PPT" -> parsePpt(in, maxChars);
                default -> throw new IllegalArgumentException("不支持的 Office 格式：" + fileType);
            };
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(fileType + " 解析失败：" + e.getMessage(), e);
        }
    }

    private List<ParsedSection> parseDocx(InputStream in, int maxChars) throws Exception {
        List<ParsedSection> sections = new ArrayList<>();
        try (XWPFDocument document = new XWPFDocument(in)) {
            int index = 0;
            int used = 0;
            for (IBodyElement element : document.getBodyElements()) {
                String text = "";
                if (element instanceof XWPFParagraph paragraph) {
                    text = paragraph.getText();
                } else if (element instanceof XWPFTable table) {
                    text = tableText(table);
                }
                text = text == null ? "" : text.trim();
                if (text.isEmpty()) {
                    continue;
                }
                index++;
                if (used + text.length() > maxChars) {
                    sections.add(new ParsedSection("第 " + index + " 段（已截断）",
                            text.substring(0, Math.max(0, maxChars - used))));
                    break;
                }
                sections.add(new ParsedSection("第 " + index + " 段", text));
                used += text.length();
            }
        }
        return sections;
    }

    private static String tableText(XWPFTable table) {
        StringBuilder sb = new StringBuilder();
        table.getRows().forEach(row ->
                row.getTableCells().forEach(cell -> sb.append(cell.getText()).append('\t')));
        return sb.toString().trim();
    }

    private List<ParsedSection> parseWorkbook(WorkbookSupplier supplier, int maxChars) throws Exception {
        List<ParsedSection> sections = new ArrayList<>();
        try (Workbook workbook = supplier.get()) {
            DataFormatter formatter = new DataFormatter();
            int used = 0;
            for (Sheet sheet : workbook) {
                StringBuilder sb = new StringBuilder();
                int rowNum = 0;
                for (Row row : sheet) {
                    StringBuilder line = new StringBuilder();
                    for (Cell cell : row) {
                        String value = formatter.formatCellValue(cell).trim();
                        if (!value.isEmpty()) {
                            line.append(value).append('\t');
                        }
                    }
                    if (line.length() > 0) {
                        sb.append(line).append('\n');
                        rowNum++;
                    }
                }
                String text = sb.toString().trim();
                if (text.isEmpty()) {
                    continue;
                }
                if (used + text.length() > maxChars) {
                    sections.add(new ParsedSection(sheet.getSheetName() + "（已截断）",
                            text.substring(0, Math.max(0, maxChars - used))));
                    break;
                }
                sections.add(new ParsedSection(sheet.getSheetName() + "（" + rowNum + " 行）", text));
                used += text.length();
            }
        }
        return sections;
    }

    private List<ParsedSection> parsePptx(InputStream in, int maxChars) throws Exception {
        List<ParsedSection> sections = new ArrayList<>();
        try (XMLSlideShow slideshow = new XMLSlideShow(in)) {
            int page = 0;
            int used = 0;
            for (org.apache.poi.xslf.usermodel.XSLFSlide slide : slideshow.getSlides()) {
                page++;
                StringBuilder sb = new StringBuilder();
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String text = textShape.getText();
                        if (text != null && !text.isBlank()) {
                            sb.append(text.trim()).append('\n');
                        }
                    }
                }
                String text = sb.toString().trim();
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
        }
        return sections;
    }

    private List<ParsedSection> parsePpt(InputStream in, int maxChars) throws Exception {
        List<ParsedSection> sections = new ArrayList<>();
        try (HSLFSlideShow slideshow = new HSLFSlideShow(in)) {
            int page = 0;
            int used = 0;
            for (org.apache.poi.hslf.usermodel.HSLFSlide slide : slideshow.getSlides()) {
                page++;
                StringBuilder sb = new StringBuilder();
                for (org.apache.poi.hslf.usermodel.HSLFShape shape : slide.getShapes()) {
                    if (shape instanceof org.apache.poi.hslf.usermodel.HSLFTextShape textShape) {
                        String text = textShape.getText();
                        if (text != null && !text.isBlank()) {
                            sb.append(text.trim()).append('\n');
                        }
                    }
                }
                String text = sb.toString().trim();
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
        }
        return sections;
    }

    private List<ParsedSection> parseDoc(InputStream in, int maxChars) throws Exception {
        try (HWPFDocument document = new HWPFDocument(in)) {
            WordExtractor extractor = new WordExtractor(document);
            String[] paragraphs = extractor.getParagraphText();
            List<ParsedSection> sections = new ArrayList<>();
            int used = 0;
            for (int i = 0; i < paragraphs.length; i++) {
                String text = paragraphs[i] == null ? "" : paragraphs[i].trim();
                if (text.isEmpty()) {
                    continue;
                }
                if (used + text.length() > maxChars) {
                    sections.add(new ParsedSection("第 " + (i + 1) + " 段（已截断）",
                            text.substring(0, Math.max(0, maxChars - used))));
                    break;
                }
                sections.add(new ParsedSection("第 " + (i + 1) + " 段", text));
                used += text.length();
            }
            return sections;
        }
    }

    @FunctionalInterface
    private interface WorkbookSupplier {
        Workbook get() throws Exception;
    }
}
