package com.lianzhi.attendance.knowledge.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 上传文件记录（sys_file）。
 *
 * <p>知识库需要留存原文件，才能在"重建索引"时重新解析而不用重新上传。
 * 存储路径不对外暴露，下载走鉴权接口。</p>
 */
@Getter
@Setter
public class SysFileRecord {

    private Long id;
    private String originalName;
    private String storagePath;
    private Long size;
    private String mime;
    private Long uploaderId;
    private String visibility;
    /**
     * visibility=DEPT 时生效的部门。
     *
     * <p>阶段 6 之前文件可见范围只有 PUBLIC/PRIVATE 两档，且上传时硬编码为 PRIVATE，
     * 导致"本部门可见"的文档其原文件谁都下载不到。这里与文档可见范围对齐。</p>
     */
    private Long ownerDeptId;
    private LocalDateTime createdAt;
}
