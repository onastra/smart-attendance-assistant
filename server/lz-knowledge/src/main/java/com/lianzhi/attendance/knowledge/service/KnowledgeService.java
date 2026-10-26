package com.lianzhi.attendance.knowledge.service;

import com.lianzhi.attendance.common.exception.BusinessException;
import com.lianzhi.attendance.common.page.PageResult;
import com.lianzhi.attendance.common.result.ResultCode;
import com.lianzhi.attendance.framework.auth.AuthUser;
import com.lianzhi.attendance.knowledge.config.KnowledgeProperties;
import com.lianzhi.attendance.knowledge.dto.DocumentView;
import com.lianzhi.attendance.knowledge.entity.KbChunk;
import com.lianzhi.attendance.knowledge.entity.KbDocument;
import com.lianzhi.attendance.knowledge.entity.KbQaLog;
import com.lianzhi.attendance.knowledge.entity.SysFileRecord;
import com.lianzhi.attendance.knowledge.mapper.KbChunkMapper;
import com.lianzhi.attendance.knowledge.mapper.KbDocumentMapper;
import com.lianzhi.attendance.knowledge.mapper.KbFileMapper;
import com.lianzhi.attendance.knowledge.mapper.KbLogMapper;
import com.lianzhi.attendance.knowledge.mapper.KbQaLogMapper;
import com.lianzhi.attendance.knowledge.parse.ChunkSplitter;
import com.lianzhi.attendance.knowledge.parse.OfficeDocumentParser;
import com.lianzhi.attendance.knowledge.parse.ParsedSection;
import com.lianzhi.attendance.knowledge.parse.PdfDocumentParser;
import com.lianzhi.attendance.knowledge.parse.TextDocumentParser;
import com.lianzhi.attendance.knowledge.query.KbDocumentQuery;
import com.lianzhi.attendance.knowledge.storage.KnowledgeFileStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 知识库文档服务：上传 → 解析 → 切片 → 索引，以及删除与重建。
 *
 * <p>解析失败的处理原则是<strong>如实失败</strong>：文档置为 FAILED 并把原因写进
 * {@code parse_message}，界面上显式显示。不让"解析不出内容"的文档混在 READY 里
 * 假装可检索——那样提问时会永远检索不到，用户无法判断是系统问题还是内容问题。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final KbDocumentMapper documentMapper;
    private final KbChunkMapper chunkMapper;
    private final KbFileMapper fileMapper;
    private final KbQaLogMapper qaLogMapper;
    private final KbLogMapper logMapper;
    private final KnowledgeFileStore fileStore;
    private final ChunkSplitter splitter;
    private final KnowledgeProperties properties;
    private final PdfDocumentParser pdfParser;
    private final OfficeDocumentParser officeParser;
    private final TextDocumentParser textParser;

    /**
     * 上传并索引文档。
     *
     * @return 已落库的文档（含最终状态）
     */
    public KbDocument upload(MultipartFile file, String visibility, Long ownerDeptId, AuthUser user) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "上传文件为空");
        }
        if (file.getSize() > properties.getMaxFileSizeBytes()) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "文件超过上限 " + (properties.getMaxFileSizeBytes() / 1024 / 1024) + " MB");
        }
        String originalName = file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename();
        String fileType = detectType(originalName);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "读取上传文件失败");
        }

        String hash = sha256(bytes);
        List<KbDocument> duplicates = documentMapper.selectByHash(hash);
        if (duplicates != null && !duplicates.isEmpty()) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "该文档内容已存在（文档 ID " + duplicates.get(0).getId() + "），请勿重复上传");
        }

        // 先定文档的可见范围，再让原文件跟它保持一致：
        // 阶段 6 之前文件可见范围被硬编码成 PRIVATE，结果是"本部门可见"的文档
        // 其原文件连本部门的人都下载不到，检索却能看到内容——口径自相矛盾。
        String docVisibility = normalizeVisibility(visibility);
        Long docOwnerDeptId = KbDocument.VISIBILITY_DEPT.equals(docVisibility) ? ownerDeptId : null;

        KnowledgeFileStore.StoredFile stored = fileStore.save(bytes, originalName);
        SysFileRecord fileRecord = new SysFileRecord();
        fileRecord.setOriginalName(originalName);
        fileRecord.setStoragePath(stored.relativePath());
        fileRecord.setSize((long) bytes.length);
        fileRecord.setMime(file.getContentType());
        fileRecord.setUploaderId(user == null ? null : user.getUserId());
        fileRecord.setVisibility(docVisibility);
        fileRecord.setOwnerDeptId(docOwnerDeptId);
        fileMapper.insert(fileRecord);

        KbDocument document = new KbDocument();
        document.setTitle(stripExtension(originalName));
        document.setFileType(fileType);
        document.setFileSize((long) bytes.length);
        document.setFileId(fileRecord.getId());
        document.setContentHash(hash);
        document.setVisibility(docVisibility);
        document.setOwnerDeptId(docOwnerDeptId);
        document.setStatus(KbDocument.STATUS_PARSING);
        document.setChunkCount(0);
        document.setCreatedBy(user == null ? null : user.getUserId());
        documentMapper.insertDocument(document);

        index(document, bytes);
        writeLog(user, "KB_UPLOAD", String.valueOf(document.getId()), 1,
                "上传文档 " + originalName + " 状态=" + document.getStatus());
        return document;
    }

    /** 重建索引：只重解析原文件，不动文档元信息 */
    public KbDocument rebuild(Long id, AuthUser user) {
        KbDocument document = requireDocument(id);
        if (document.getFileId() == null) {
            throw new BusinessException(ResultCode.CONFLICT, "该文档没有留存原文件，无法重建，请重新上传");
        }
        SysFileRecord fileRecord = fileMapper.selectById(document.getFileId());
        if (fileRecord == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "原文件记录已丢失，无法重建");
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(fileStore.resolve(fileRecord.getStoragePath()));
        } catch (Exception e) {
            throw new BusinessException(ResultCode.NOT_FOUND, "读取原文件失败：" + e.getMessage());
        }
        documentMapper.updateParseResult(id, KbDocument.STATUS_PARSING, 0, null);
        chunkMapper.deleteByDocumentId(id);
        index(document, bytes);
        writeLog(user, "KB_REBUILD", String.valueOf(id), 1, "重建索引 状态=" + document.getStatus());
        return documentMapper.selectById(id);
    }

    public void delete(Long id, AuthUser user) {
        KbDocument document = requireDocument(id);
        chunkMapper.deleteByDocumentId(id);
        documentMapper.deleteById(id);
        if (document.getFileId() != null) {
            SysFileRecord fileRecord = fileMapper.selectById(document.getFileId());
            if (fileRecord != null) {
                fileStore.delete(fileRecord.getStoragePath());
                fileMapper.deleteById(fileRecord.getId());
            }
        }
        writeLog(user, "KB_DELETE", String.valueOf(id), 1, "删除文档 " + document.getTitle());
    }

    public PageResult<DocumentView> list(KbDocumentQuery query, AuthUser user) {
        List<KbDocument> docs = documentMapper.selectPage(query);
        long total = documentMapper.countPage(query);
        List<DocumentView> views = docs.stream()
                .map(doc -> DocumentView.of(doc, canRead(doc, user),
                        doc.getCreatedAt() == null ? null : doc.getCreatedAt().format(TIME)))
                .toList();
        return PageResult.of(query, total, views);
    }

    /** 最近问答日志；只含问题与统计，不含答案正文 */
    public List<KbQaLog> recentLogs(Long userId, int limit) {
        return qaLogMapper.selectRecent(userId, limit);
    }

    /** 修改可见范围 */
    public KbDocument updateVisibility(Long id, String visibility, Long ownerDeptId, AuthUser user) {
        KbDocument document = requireDocument(id);
        String normalized = normalizeVisibility(visibility);
        Long deptId = KbDocument.VISIBILITY_DEPT.equals(normalized) ? ownerDeptId : null;
        if (KbDocument.VISIBILITY_DEPT.equals(normalized) && deptId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "可见范围选了部门，必须指定部门");
        }
        documentMapper.updateVisibility(id, normalized, deptId);
        writeLog(user, "KB_VISIBILITY", String.valueOf(id), 1, "可见范围改为 " + normalized);
        return documentMapper.selectById(id);
    }

    // ---------------- 内部实现 ----------------

    /** 解析 + 切片 + 落切片表 + 回写状态；任何失败都转为 FAILED 并写明原因 */
    private void index(KbDocument document, byte[] bytes) {
        try {
            List<ParsedSection> sections = parse(document.getFileType(), bytes);
            if (sections.isEmpty()) {
                fail(document, "未能从文件中抽取到文本内容"
                        + "（扫描版 PDF 或纯图片文档没有文本层，本项目不做 OCR）");
                return;
            }
            List<KbChunk> chunks = splitter.split(document.getId(), sections);
            if (chunks.isEmpty()) {
                fail(document, "切片结果为空");
                return;
            }
            chunkMapper.deleteByDocumentId(document.getId());
            chunkMapper.insertBatch(chunks);
            document.setChunkCount(chunks.size());
            document.setStatus(KbDocument.STATUS_READY);
            document.setParseMessage(null);
            documentMapper.updateParseResult(document.getId(), KbDocument.STATUS_READY,
                    chunks.size(), null);
        } catch (Exception e) {
            log.warn("[KB] 文档解析失败 id={} type={}：{}", document.getId(), document.getFileType(),
                    e.getMessage());
            fail(document, e.getMessage());
        }
    }

    private void fail(KbDocument document, String message) {
        document.setStatus(KbDocument.STATUS_FAILED);
        document.setChunkCount(0);
        document.setParseMessage(truncate(message, 500));
        documentMapper.updateParseResult(document.getId(), KbDocument.STATUS_FAILED, 0,
                document.getParseMessage());
    }

    private List<ParsedSection> parse(String fileType, byte[] bytes) {
        int maxChars = properties.getMaxCharsPerDocument();
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            if ("PDF".equals(fileType)) {
                return pdfParser.parse(in, maxChars);
            }
            if (Set.of("DOCX", "XLSX", "PPTX", "DOC", "XLS", "PPT").contains(fileType)) {
                return officeParser.parseTyped(in, fileType, maxChars);
            }
            if (Set.of("TXT", "MD").contains(fileType)) {
                return textParser.parse(in, maxChars);
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取失败：" + e.getMessage(), e);
        }
        throw new IllegalStateException("不支持的文件类型：" + fileType);
    }

    private String detectType(String originalName) {
        String ext = originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT)
                : "";
        Set<String> allowed = Set.of(properties.getAllowedExtensions().split(","));
        if (ext.isEmpty() || !allowed.contains(ext.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "不支持的文件类型 ." + ext + "，当前支持：" + properties.getAllowedExtensions());
        }
        return ext;
    }

    private String normalizeVisibility(String visibility) {
        if (visibility == null || visibility.isBlank()) {
            return KbDocument.VISIBILITY_PUBLIC;
        }
        String upper = visibility.trim().toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "PUBLIC", "DEPT", "PRIVATE" -> upper;
            default -> throw new BusinessException(ResultCode.PARAM_ERROR,
                    "可见范围只能是 PUBLIC / DEPT / PRIVATE");
        };
    }

    /**
     * 判断当前用户能否阅读该文档。
     *
     * <p>与 SQL 里的过滤条件<strong>必须保持一致</strong>，否则会出现
     * "列表里显示无权、检索却能命中"或反之的口径漂移。</p>
     */
    public boolean canRead(KbDocument document, AuthUser user) {
        if (document == null) {
            return false;
        }
        if (KbDocument.VISIBILITY_PUBLIC.equals(document.getVisibility())) {
            return true;
        }
        if (user == null) {
            return false;
        }
        if (KbDocument.VISIBILITY_PRIVATE.equals(document.getVisibility())) {
            return document.getCreatedBy() != null && document.getCreatedBy().equals(user.getUserId());
        }
        if (KbDocument.VISIBILITY_DEPT.equals(document.getVisibility())) {
            Long deptId = user.getEmployeeId() == null
                    ? null : documentMapper.selectEmployeeDeptId(user.getEmployeeId());
            return deptId != null && deptId.equals(document.getOwnerDeptId());
        }
        return false;
    }

    private KbDocument requireDocument(Long id) {
        KbDocument document = documentMapper.selectById(id);
        if (document == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文档不存在");
        }
        return document;
    }

    private void writeLog(AuthUser user, String action, String targetId, int success, String message) {
        try {
            logMapper.insertOperationLog(user == null ? null : user.getUserId(),
                    "knowledge", action, targetId, success, truncate(message, 500));
        } catch (Exception e) {
            log.warn("[KB] 操作日志落库失败：{}", e.getMessage());
        }
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
