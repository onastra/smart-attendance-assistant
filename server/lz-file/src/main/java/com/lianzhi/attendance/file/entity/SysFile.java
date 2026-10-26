package com.lianzhi.attendance.file.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 上传文件记录（sys_file）。
 *
 * <p>与 lz-knowledge 里的 SysFileRecord 指向同一张表。模块之间不产生编译期依赖
 * （见 docs/architecture.md），所以这里各自持有一份实体，而不是互相引用。</p>
 *
 * <p>可见范围三档与知识库文档一致：PUBLIC / DEPT / PRIVATE。
 * DEPT 时 {@code ownerDeptId} 必填，否则无法判定"同部门"。</p>
 */
@Getter
@Setter
public class SysFile {

    public static final String VISIBILITY_PUBLIC = "PUBLIC";
    public static final String VISIBILITY_DEPT = "DEPT";
    public static final String VISIBILITY_PRIVATE = "PRIVATE";

    private Long id;
    private String originalName;
    /** 存储相对路径：只对内使用，绝不下发到前端 */
    private String storagePath;
    private Long size;
    private String mime;
    private Long uploaderId;
    private String visibility;
    private Long ownerDeptId;
    private LocalDateTime createdAt;

    /** 冗余展示字段：上传人姓名 */
    private String uploaderName;
}
