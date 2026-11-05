package com.lianzhi.attendance.framework.auth;

import java.util.Set;

/**
 * 权限数据源接口。
 *
 * <p>框架层只定义契约，具体实现由 <code>lz-system</code> 模块提供，
 * 以此避免 framework 反向依赖业务模块。</p>
 */
public interface PermissionChecker {

    /** 查询指定用户拥有的全部权限码。 */
    Set<String> permissionsOf(Long userId);

    /**
     * 查询指定用户拥有的全部角色编码（ADMIN / MANAGER / EMPLOYEE…）。
     *
     * <p>默认空实现保证已有自定义实现类无需同步修改；业务上需要按角色
     * 硬校验（如知识库写操作仅限管理员）时依赖此方法。</p>
     */
    default Set<String> rolesOf(Long userId) {
        return java.util.Collections.emptySet();
    }

    /** 判断用户是否拥有某权限码。 */
    default boolean hasPermission(Long userId, String permissionCode) {
        Set<String> codes = permissionsOf(userId);
        return codes != null && codes.contains(permissionCode);
    }

    /** 判断用户是否拥有某角色。 */
    default boolean hasRole(Long userId, String roleCode) {
        Set<String> codes = rolesOf(userId);
        return codes != null && codes.contains(roleCode);
    }
}
