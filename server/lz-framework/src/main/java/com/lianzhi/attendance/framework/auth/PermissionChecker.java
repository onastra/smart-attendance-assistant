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

    /** 判断用户是否拥有某权限码。 */
    default boolean hasPermission(Long userId, String permissionCode) {
        Set<String> codes = permissionsOf(userId);
        return codes != null && codes.contains(permissionCode);
    }
}
