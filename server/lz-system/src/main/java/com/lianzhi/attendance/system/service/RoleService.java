package com.lianzhi.attendance.system.service;

import com.lianzhi.attendance.common.exception.BusinessException;
import com.lianzhi.attendance.common.page.PageResult;
import com.lianzhi.attendance.common.result.ResultCode;
import com.lianzhi.attendance.system.entity.SysPermission;
import com.lianzhi.attendance.system.entity.SysRole;
import com.lianzhi.attendance.system.mapper.SysRoleMapper;
import com.lianzhi.attendance.system.query.RoleQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 角色与权限授权。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {

    private final SysRoleMapper sysRoleMapper;

    public PageResult<SysRole> page(RoleQuery query) {
        long total = sysRoleMapper.countByQuery(query);
        if (total == 0) {
            return PageResult.empty(query);
        }
        return PageResult.of(query, total, sysRoleMapper.selectByQuery(query));
    }

    public List<SysPermission> listAllPermissions() {
        return sysRoleMapper.selectAllPermissions();
    }

    public List<Long> permissionIdsOf(Long roleId) {
        return sysRoleMapper.selectPermissionIdsByRoleId(roleId);
    }

    @Transactional
    public Long create(SysRole role, List<Long> permissionIds) {
        if (!StringUtils.hasText(role.getRoleCode())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "角色编码不能为空");
        }
        if (sysRoleMapper.countByRoleCode(role.getRoleCode(), null) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "角色编码已存在");
        }
        if (role.getStatus() == null) {
            role.setStatus(1);
        }
        sysRoleMapper.insert(role);
        grant(role.getId(), permissionIds);
        log.info("[ROLE] 新建角色 id={} code={}", role.getId(), role.getRoleCode());
        return role.getId();
    }

    @Transactional
    public void update(SysRole role, List<Long> permissionIds) {
        requireRole(role.getId());
        if (StringUtils.hasText(role.getRoleCode())
                && sysRoleMapper.countByRoleCode(role.getRoleCode(), role.getId()) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "角色编码已被其他角色占用");
        }
        sysRoleMapper.updateById(role);
        if (permissionIds != null) {
            grant(role.getId(), permissionIds);
        }
        log.info("[ROLE] 编辑角色 id={}", role.getId());
    }

    @Transactional
    public void grant(Long roleId, List<Long> permissionIds) {
        requireRole(roleId);
        sysRoleMapper.deletePermissionsByRoleId(roleId);
        if (permissionIds != null && !permissionIds.isEmpty()) {
            sysRoleMapper.insertRolePermissions(roleId, permissionIds);
        }
        log.info("[ROLE] 授权角色 id={} permissionCount={}",
                roleId, permissionIds == null ? 0 : permissionIds.size());
    }

    /**
     * 删除角色。
     *
     * <p>仍有账号引用时拒绝删除：直接删会让这些账号静默失去全部权限，
     * 属于"删了才知道后果"的危险操作，宁可要求先解除绑定。</p>
     */
    @Transactional
    public void delete(Long roleId) {
        requireRole(roleId);
        if (sysRoleMapper.countUsersByRoleId(roleId) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "该角色下仍有关联账号，请先解除绑定");
        }
        sysRoleMapper.deletePermissionsByRoleId(roleId);
        SysRole patch = new SysRole();
        patch.setId(roleId);
        patch.setStatus(0);
        sysRoleMapper.updateById(patch);
        log.info("[ROLE] 停用并清空角色权限 id={}", roleId);
    }

    private SysRole requireRole(Long id) {
        SysRole role = sysRoleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "角色不存在");
        }
        return role;
    }
}
