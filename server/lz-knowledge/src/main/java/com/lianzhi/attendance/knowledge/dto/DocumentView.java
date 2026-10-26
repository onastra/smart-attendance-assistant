package com.lianzhi.attendance.knowledge.dto;

import com.lianzhi.attendance.knowledge.entity.KbDocument;
import lombok.Getter;
import lombok.Setter;

/**
 * 文档列表视图。
 *
 * <p>额外带 {@code canRead}：列表里会出现自己无权阅读的文档（否则管理员无法管理），
 * 但前端必须据此提示"无权查看"，避免用户点进去才发现检索不到。</p>
 */
@Getter
@Setter
public class DocumentView {

    private Long id;
    private String title;
    private String fileType;
    private Long fileSize;
    private String visibility;
    private Long ownerDeptId;
    private String ownerDeptName;
    private String status;
    private String parseMessage;
    private Integer chunkCount;
    private Long createdBy;
    private String createdByName;
    private String createdAt;
    private boolean canRead;

    public static DocumentView of(KbDocument doc, boolean canRead, String createdAt) {
        DocumentView view = new DocumentView();
        view.setId(doc.getId());
        view.setTitle(doc.getTitle());
        view.setFileType(doc.getFileType());
        view.setFileSize(doc.getFileSize());
        view.setVisibility(doc.getVisibility());
        view.setOwnerDeptId(doc.getOwnerDeptId());
        view.setOwnerDeptName(doc.getOwnerDeptName());
        view.setStatus(doc.getStatus());
        view.setParseMessage(doc.getParseMessage());
        view.setChunkCount(doc.getChunkCount());
        view.setCreatedBy(doc.getCreatedBy());
        view.setCreatedByName(doc.getCreatedByName());
        view.setCreatedAt(createdAt);
        view.setCanRead(canRead);
        return view;
    }
}
