package com.lianzhi.attendance.system.entity;

import lombok.Data;

/**
 * 权限码定义。拦截器按 <code>perm_code</code> 比对，因此该字段一旦上线不可随意重命名。
 */
@Data
public class SysPermission {

    private Long id;
    private String permCode;
    private String permName;
    private String module;
}
