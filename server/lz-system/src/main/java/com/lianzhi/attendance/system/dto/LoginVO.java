package com.lianzhi.attendance.system.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Set;

/**
 * 登录成功返回体。
 *
 * <p>注意：任何分支都不得携带 <code>password_hash</code>，
 * 这是本项目的安全红线之一。</p>
 */
@Getter
@Setter
@Builder
public class LoginVO {

    private String token;
    /** 令牌有效秒数，前端据此安排刷新或过期提示 */
    private long expireSeconds;
    private Long userId;
    private String username;
    private String nickname;
    private Long employeeId;
    /** 该用户持有的角色编码，前端据此渲染菜单 */
    private List<String> roles;
    private Set<String> permissions;
}
