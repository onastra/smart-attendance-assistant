package com.lianzhi.attendance.system.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公司 / 子公司。
 *
 * <p><code>parent_id = 0</code> 表示总公司，其余指向上级公司，支持集团型多级结构。</p>
 */
@Data
public class Company {

    private Long id;
    private String companyName;
    private String companyCode;
    private Long parentId;
    private Integer status;
    private String remark;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
