package com.lianzhi.attendance.system.entity;

import lombok.Data;

/**
 * 角色。通过 <code>sys_role_permission</code> 关联权限码。
 */
@Data
public class SysRole {

    private Long id;
    private String roleCode;
    private String roleName;
    private String description;
    private Integer status;
}
