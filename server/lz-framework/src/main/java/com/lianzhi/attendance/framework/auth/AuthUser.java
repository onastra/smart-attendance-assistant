package com.lianzhi.attendance.framework.auth;

import lombok.Getter;
import lombok.Setter;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 当前登录用户上下文（线程隔离）。
 *
 * <p>拦截器在请求进入时写入，请求结束时必须清除，避免线程池复用导致用户串号。</p>
 */
@Getter
@Setter
public class AuthUser {

    private Long userId;
    private String username;
    /** 员工档案主键；尚未建档的用户为 null */
    private Long employeeId;
    /** 该用户拥有的权限码集合 */
    private Set<String> permissions = Collections.emptySet();

    public boolean hasPermission(String permissionCode) {
        return permissions != null && permissions.contains(permissionCode);
    }

    public void addPermissions(Set<String> codes) {
        Set<String> merged = new HashSet<>(this.permissions == null ? Collections.emptySet() : this.permissions);
        if (codes != null) {
            merged.addAll(codes);
        }
        this.permissions = merged;
    }
}
