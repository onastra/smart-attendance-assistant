package com.lianzhi.attendance.knowledge.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 知识库文档。
 *
 * <p>文档从"上传"到"可被检索"要经历 PARSING → READY 两个阶段；解析失败时落 FAILED
 * 并把原因写进 {@code parseMessage}，不允许静默吞掉异常后仍显示成功。</p>
 */
@Getter
@Setter
public class KbDocument {

    public static final String STATUS_PARSING = "PARSING";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_FAILED = "FAILED";

    public static final String VISIBILITY_PUBLIC = "PUBLIC";
    public static final String VISIBILITY_DEPT = "DEPT";
    public static final String VISIBILITY_PRIVATE = "PRIVATE";

    private Long id;
    private String title;
    /** PDF / DOCX / XLSX / PPTX / DOC / XLS / PPT / TXT / MD */
    private String fileType;
    private Long fileSize;
    private Long fileId;
    /** 内容指纹（SHA-256），用于上传去重 */
    private String contentHash;
    private String visibility;
    /** visibility=DEPT 时生效；其余情况为 null */
    private Long ownerDeptId;
    private String status;
    private String parseMessage;
    private Integer chunkCount;
    private Long createdBy;
    private LocalDateTime createdAt;

    /** 冗余展示字段：部门名、上传人姓名、状态中文名 */
    private String ownerDeptName;
    private String createdByName;
}
