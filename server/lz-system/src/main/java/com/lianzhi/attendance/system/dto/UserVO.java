package com.lianzhi.attendance.system.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 账号出参。
 *
 * <p>刻意不包含 <code>passwordHash</code>：口令散列只允许在服务端参与比对，
 * 不允许出现在任何对外响应的字段里。</p>
 */
@Data
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private Long employeeId;
    private Integer status;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    private List<String> roles;
    private List<Long> roleIds;
}
