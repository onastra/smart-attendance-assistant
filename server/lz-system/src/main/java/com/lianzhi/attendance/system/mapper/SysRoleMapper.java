package com.lianzhi.attendance.system.mapper;

import com.lianzhi.attendance.system.entity.SysPermission;
import com.lianzhi.attendance.system.entity.SysRole;
import com.lianzhi.attendance.system.query.RoleQuery;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;

/**
 * 角色与权限数据访问。
 */
public interface SysRoleMapper {

    List<SysRole> selectByQuery(RoleQuery query);

    /** 查询全部启用角色，供前端下拉选择 */
    List<SysRole> selectAllRoles();

    long countByQuery(RoleQuery query);

    SysRole selectById(@Param("id") Long id);

    int insert(SysRole role);

    int updateById(SysRole role);

    long countByRoleCode(@Param("roleCode") String roleCode, @Param("excludeId") Long excludeId);

    /** 查询某用户拥有的角色列表 */
    List<SysRole> selectRolesByUserId(@Param("userId") Long userId);

    /** 查询某角色已授权的权限 ID */
    List<Long> selectPermissionIdsByRoleId(@Param("roleId") Long roleId);

    /** 判断角色是否仍被账号引用，用于阻止直接删除角色导致授权关系丢失 */
    long countUsersByRoleId(@Param("roleId") Long roleId);

    /** 全量覆盖式授权：先删后插 */
    int deletePermissionsByRoleId(@Param("roleId") Long roleId);

    int insertRolePermissions(@Param("roleId") Long roleId, @Param("permissionIds") List<Long> permissionIds);

    List<SysPermission> selectAllPermissions();

    /** 直接查询某用户的全部权限码（权限校验热路径） */
    Set<String> selectPermCodesByUserId(@Param("userId") Long userId);
}
