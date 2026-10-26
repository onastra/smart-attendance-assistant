package com.lianzhi.attendance.system.entity;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 部门。支持树形层级，<code>parent_id = 0</code> 为公司下的顶级部门。
 */
@Data
public class Department {

    private Long id;
    private Long companyId;
    private String deptName;
    private Long parentId;
    private Long leaderEmployeeId;
    private Integer sortNo;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ===== 展示用辅助字段 =====
    private String companyName;
    private String leaderName;
    /** 树形接口填充，非数据库列 */
    private List<Department> children;
}
