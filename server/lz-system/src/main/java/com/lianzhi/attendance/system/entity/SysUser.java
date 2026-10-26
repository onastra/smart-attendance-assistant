package com.lianzhi.attendance.system.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录账号。
 *
 * <p>与员工档案分离：<code>employee_id</code> 可为空，允许"尚未建档的后勤账号"存在。</p>
 */
@Data
public class SysUser {

    private Long id;
    private String username;
    /** BCrypt 摘要，任何情况下不返回给前端 */
    private String passwordHash;
    private String nickname;
    private Long employeeId;
    /** 1 启用 0 停用 */
    private Integer status;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
