package com.lianzhi.attendance.system.controller;

import com.lianzhi.attendance.common.page.PageResult;
import com.lianzhi.attendance.common.result.R;
import com.lianzhi.attendance.framework.auth.RequirePermission;
import com.lianzhi.attendance.system.entity.SysPermission;
import com.lianzhi.attendance.system.entity.SysRole;
import com.lianzhi.attendance.system.query.RoleQuery;
import com.lianzhi.attendance.system.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 角色管理接口。
 */
@Tag(name = "角色管理", description = "角色的增删改查与权限分配")
@RestController
@RequestMapping("/api/system/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @Operation(summary = "角色分页列表")
    @GetMapping
    @RequirePermission("system:role:list")
    public R<PageResult<SysRole>> page(RoleQuery query) {
        return R.ok(roleService.page(query));
    }

    @Operation(summary = "全部权限码", description = "返回系统内全部权限定义，用于授权时勾选")
    @GetMapping("/permissions")
    @RequirePermission("system:role:list")
    public R<List<SysPermission>> permissions() {
        return R.ok(roleService.listAllPermissions());
    }

    @Operation(summary = "某角色已授权的权限 ID")
    @GetMapping("/{id}/permissions")
    @RequirePermission("system:role:list")
    public R<List<Long>> permissionIds(@PathVariable Long id) {
        return R.ok(roleService.permissionIdsOf(id));
    }

    @Operation(summary = "新建角色")
    @PostMapping
    @RequirePermission("system:role:create")
    public R<Map<String, Object>> create(@RequestBody RoleGrantRequest request) {
        Long id = roleService.create(request.getRole(), request.getPermissionIds());
        return R.ok(Map.of("id", id));
    }

    @Operation(summary = "编辑角色", description = "permissionIds 传入即全量覆盖授权")
    @PutMapping("/{id}")
    @RequirePermission("system:role:update")
    public R<Void> update(@PathVariable Long id, @RequestBody RoleGrantRequest request) {
        request.getRole().setId(id);
        roleService.update(request.getRole(), request.getPermissionIds());
        return R.ok();
    }

    @Operation(summary = "给角色分配权限", description = "全量覆盖式授权")
    @PostMapping("/{id}/permissions")
    @RequirePermission("system:role:grant")
    public R<Void> grant(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        roleService.grant(id, body.get("permissionIds"));
        return R.ok();
    }

    @Operation(summary = "删除角色", description = "仍有账号关联时拒绝删除")
    @DeleteMapping("/{id}")
    @RequirePermission("system:role:delete")
    public R<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return R.ok();
    }

    /**
     * 角色与权限的复合入参载体，避免"请求体既要角色字段又要权限数组"时
     * 直接复用实体导致权限字段污染实体语义。
     */
    public static class RoleGrantRequest {
        private SysRole role;
        private List<Long> permissionIds;

        public SysRole getRole() {
            return role == null ? new SysRole() : role;
        }

        public void setRole(SysRole role) {
            this.role = role;
        }

        public List<Long> getPermissionIds() {
            return permissionIds;
        }

        public void setPermissionIds(List<Long> permissionIds) {
            this.permissionIds = permissionIds;
        }

        @SuppressWarnings("unused")
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("role", role);
            map.put("permissionIds", permissionIds);
            return map;
        }
    }
}
