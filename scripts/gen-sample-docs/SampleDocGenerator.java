package samples;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 生成知识库演示文档（PDF / DOCX / XLSX / PPTX / MD）。
 *
 * <p>存在的理由：验证阶段 5 的解析器必须拿<strong>真实格式的文件</strong>去跑。
 * 用手搓的假二进制或改扩展名的文本蒙混过关，等于什么都没验证。
 * 内容取自本项目的真实制度口径（考勤规则、请假流程、加班与出差），
 * 这样演示时提问才能命中，也顺带让知识库有实际内容可查。</p>
 *
 * <p>运行方式（需要先 mvn 拉过依赖）：
 * <pre>
 *   cd server
 *   mvn -q dependency:build-classpath -pl lz-knowledge -Dmdep.outputFile=target/kb-cp.txt
 *   java --class-path "$(cat lz-knowledge/target/kb-cp.txt)" \
 *        scripts/gen-sample-docs/SampleDocGenerator.java &lt;输出目录&gt;
 * </pre>
 * </p>
 */
public class SampleDocGenerator {

    public static void main(String[] args) throws Exception {
        Path dir = Paths.get(args.length > 0 ? args[0] : "docs/samples/kb");
        Files.createDirectories(dir);

        writeMarkdown(dir.resolve("考勤管理制度.md"));
        writeDocx(dir.resolve("员工请假与加班管理办法.docx"));
        writeXlsx(dir.resolve("考勤参数速查表.xlsx"));
        writePptx(dir.resolve("新员工考勤系统使用培训.pptx"));
        writePdf(dir.resolve("办公场所与打卡点说明.pdf"));

        System.out.println("样例文档已生成到：" + dir.toAbsolutePath());
        for (Path file : Files.list(dir).sorted().toList()) {
            System.out.println("  - " + file.getFileName() + " (" + Files.size(file) + " bytes)");
        }
    }

    // ---------------- Markdown / TXT ----------------

    private static void writeMarkdown(Path target) throws Exception {
        String content = """
                # 考勤管理制度（2026 版）

                ## 一、工作时间
                公司实行标准工时制，上班时间 09:00，下班时间 18:00。
                上班打卡晚于 09:15 记为迟到，下班打卡早于 18:00 记为早退。
                迟到宽限为 15 分钟，早退不设宽限。

                ## 二、打卡方式
                员工需在办公场所扫描现场二维码完成打卡，二维码由管理员在后台签发。
                动态二维码有效期 120 秒，过期自动失效；长期固定码有效期 24 小时，可由管理员手动失效。
                二维码内容带签名校验，截屏转发或篡改参数都无法通过校验。

                ## 三、外勤与场所绑定
                员工只能在其绑定的办公场所打卡。未绑定该场所的员工扫码会被拒绝并提示原因。
                打卡时若上报定位坐标，系统按场所覆盖半径 200 米做围栏校验；未上报坐标则跳过校验并在记录中标注。

                ## 四、重复打卡
                同一员工在同一场所、同一类型（上班/下班）5 分钟内的第二次提交视为重复打卡，系统拒绝并记录。

                ## 五、考勤结果
                日汇总每天凌晨自动重算，状态分为正常、迟到、早退、缺勤、休息五种。
                迟到与早退同时发生时记迟到。出勤率的分母扣除休息日。
                """;
        Files.write(target, content.getBytes(StandardCharsets.UTF_8));
    }

    // ---------------- DOCX ----------------

    private static void writeDocx(Path target) throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            addParagraph(document, "员工请假与加班管理办法");
            addParagraph(document, "第一章 请假");
            addParagraph(document, "员工请假须提前在系统提交申请，写明假别、起止时间与事由。");
            addParagraph(document, "事假最小单位为 1 小时；病假需附医院证明。");
            addParagraph(document, "请假申请提交后进入待办，由直属主管审批；主管可批准或驳回，驳回必须填写备注。");
            addParagraph(document, "第二章 加班");
            addParagraph(document, "加班须事前申请，未申请的加班不计入统计。");
            addParagraph(document, "工作日加班按 1.5 倍计，休息日加班按 2 倍计，法定节假日按 3 倍计。");
            addParagraph(document, "第三章 出差");
            addParagraph(document, "出差申请需写明目的地与行程，出差期间的打卡按外勤规则处理。");
            document.write(Files.newOutputStream(target));
        }
    }

    private static void addParagraph(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.createRun().setText(text);
    }

    // ---------------- XLSX ----------------

    private static void writeXlsx(Path target) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("考勤参数");
            String[][] rows = {
                    {"参数项", "取值", "说明"},
                    {"上班时间", "09:00", "全局统一，暂不按部门配置班次"},
                    {"下班时间", "18:00", "全局统一"},
                    {"迟到宽限", "15 分钟", "上班打卡晚于 09:15 记迟到"},
                    {"早退宽限", "0 分钟", "下班早于 18:00 即记早退"},
                    {"重复打卡窗口", "5 分钟", "同场所同类型 5 分钟内二次提交拒绝"},
                    {"动态码有效期", "120 秒", "前端倒计时自动刷新"},
                    {"长期码有效期", "24 小时", "管理员可手动失效"},
                    {"场所覆盖半径", "200 米", "上报坐标时做围栏校验"},
                    {"日汇总重算时间", "每日 01:30", "重算昨天与今天"},
            };
            for (int i = 0; i < rows.length; i++) {
                Row row = sheet.createRow(i);
                for (int j = 0; j < rows[i].length; j++) {
                    row.createCell(j).setCellValue(rows[i][j]);
                }
            }
            workbook.write(Files.newOutputStream(target));
        }
    }

    // ---------------- PPTX ----------------

    private static void writePptx(Path target) throws Exception {
        try (XMLSlideShow slideshow = new XMLSlideShow()) {
            addSlide(slideshow, "新员工考勤系统使用培训",
                    "一、扫码打卡流程\n二、常见问题\n三、请假与加班");
            addSlide(slideshow, "一、扫码打卡流程",
                    "1. 到达办公场所后打开 H5 打卡页\n"
                            + "2. 扫描现场二维码（或手动粘贴二维码内容）\n"
                            + "3. 允许浏览器获取定位以完成围栏校验\n"
                            + "4. 看到“打卡成功”即完成");
            addSlide(slideshow, "二、常见问题",
                    "二维码过期：动态码 120 秒过期，请刷新页面重新扫描。\n"
                            + "提示非本场所：你未绑定该办公场所，请联系管理员绑定。\n"
                            + "提示重复打卡：5 分钟内已打过同类型卡，无需重复提交。\n"
                            + "提示超出范围：定位距离场所超过 200 米。");
            addSlide(slideshow, "三、请假与加班",
                    "请假与加班在系统内提交申请，由直属主管审批。\n"
                            + "本批次未开放审批功能，按项目决议暂缓实现。");
            slideshow.write(Files.newOutputStream(target));
        }
    }

    private static void addSlide(XMLSlideShow slideshow, String title, String body) {
        XSLFSlide slide = slideshow.createSlide();
        XSLFTextBox titleBox = slide.createTextBox();
        titleBox.setText(title);
        titleBox.setAnchor(new java.awt.Rectangle(50, 40, 600, 60));
        XSLFTextBox bodyBox = slide.createTextBox();
        bodyBox.setText(body);
        bodyBox.setAnchor(new java.awt.Rectangle(50, 120, 600, 300));
    }

    // ---------------- PDF ----------------

    private static void writePdf(Path target) throws Exception {
        // PDFBox 自带的 Standard 14 字体不含中文，这里用系统字体；
        // 找不到就退回英文内容，并在控制台说明——宁可内容变样，也不生成一个打不开的文件
        String fontPath = findChineseFont();
        try (PDDocument document = new PDDocument()) {
            if (fontPath == null) {
                writePdfPage(document, null, new String[]{
                        "Office Place and Check-in Points",
                        "HQ: Building A, 3F. Covered radius 200 m.",
                        "Branch: Building B, 1F. Covered radius 200 m.",
                        "Each employee can only check in at a bound place."
                });
                System.out.println("[提示] 未找到中文字体，PDF 样例改用英文内容");
            } else {
                try (FileInputStream fontStream = new FileInputStream(fontPath)) {
                    PDType0Font font = PDType0Font.load(document, fontStream);
                    writePdfPage(document, font, new String[]{
                            "办公场所与打卡点说明",
                            "总部：A 座 3 层，覆盖半径 200 米。",
                            "分部：B 座 1 层，覆盖半径 200 米。",
                            "员工只能在已绑定的场所打卡，未绑定会被拒绝。",
                            "场所二维码由管理员签发，动态码 120 秒过期。"
                    });
                }
            }
            document.save(Files.newOutputStream(target));
        }
    }

    private static void writePdfPage(PDDocument document, PDType0Font font, String[] lines) throws Exception {
        PDPage page = new PDPage();
        document.addPage(page);
        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            float y = 750;
            int size = font == null ? 14 : 16;
            if (font != null) {
                stream.setFont(font, size);
            } else {
                stream.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA, size);
            }
            stream.beginText();
            stream.newLineAtOffset(60, y);
            for (String line : lines) {
                stream.showText(line);
                y -= 28;
                stream.newLineAtOffset(0, -28);
            }
            stream.endText();
        }
    }

    private static String findChineseFont() {
        // 只挑 .ttf：PDFBox 2.x 的 PDType0Font.load 无法解析 .ttc 字体集合
        // （msyh.ttc 会直接抛 'head' table is mandatory），这里避开这类文件
        String[] candidates = {
                "C:/Windows/Fonts/simhei.ttf",
                "C:/Windows/Fonts/simsunb.ttf",
                "C:/Windows/Fonts/simkai.ttf",
                "C:/Windows/Fonts/Deng.ttf"
        };
        for (String candidate : candidates) {
            if (Files.exists(Paths.get(candidate))) {
                return candidate;
            }
        }
        return null;
    }
}
