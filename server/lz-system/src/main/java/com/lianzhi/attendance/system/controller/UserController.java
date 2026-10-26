package com.lianzhi.attendance.system.controller;

import com.lianzhi.attendance.common.page.PageResult;
import com.lianzhi.attendance.common.result.R;
import com.lianzhi.attendance.common.result.ResultCode;
import com.lianzhi.attendance.framework.auth.RequirePermission;
import com.lianzhi.attendance.system.dto.UserCreateRequest;
import com.lianzhi.attendance.system.dto.UserUpdateRequest;
import com.lianzhi.attendance.system.dto.UserVO;
import com.lianzhi.attendance.system.entity.SysRole;
import com.lianzhi.attendance.system.entity.SysUser;
import com.lianzhi.attendance.system.query.UserQuery;
import com.lianzhi.attendance.system.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
 * 账号管理接口。
 *
 * <p>权限码与本模块注解一一对应，权限数据源见 {@code sys_permission} 表。</p>
 */
@Tag(name = "账号管理", description = "登录账号的增删改查、启停与口令重置")
@RestController
@RequestMapping("/api/system/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "账号分页列表")
    @GetMapping
    @RequirePermission("system:user:list")
    public R<PageResult<UserVO>> page(UserQuery query) {
        return R.ok(userService.page(query));
    }

    @Operation(summary = "全部角色", description = "用于账号编辑时的角色下拉框")
    @GetMapping("/roles")
    @RequirePermission("system:user:list")
    public R<List<SysRole>> roles() {
        return R.ok(userService.listAllRoles());
    }

    @Operation(summary = "新建账号")
    @PostMapping
    @RequirePermission("system:user:create")
    public R<Map<String, Object>> create(@Valid @RequestBody UserCreateRequest request) {
        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setNickname(request.getNickname());
        user.setEmployeeId(request.getEmployeeId());
        user.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        Long id = userService.create(user, request.getPassword(), request.getRoleIds());
        return R.ok(Map.of("id", id));
    }

    @Operation(summary = "编辑账号", description = "roleIds 传入即全量覆盖角色关联")
    @PutMapping("/{id}")
    @RequirePermission("system:user:update")
    public R<Void> update(@PathVariable Long id, @RequestBody UserUpdateRequest request) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(request.getUsername());
        user.setNickname(request.getNickname());
        user.setEmployeeId(request.getEmployeeId());
        user.setStatus(request.getStatus());
        userService.update(user, request.getRoleIds());
        return R.ok();
    }

    @Operation(summary = "启用/停用账号", description = "停用后该账号无法登录，历史数据保留")
    @PostMapping("/{id}/status")
    @RequirePermission("system:user:update")
    public R<Void> changeStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        Integer status = body.get("status");
        if (status == null || (status != 0 && status != 1)) {
            return R.fail(ResultCode.PARAM_ERROR, "status 只能为 0（停用）或 1（启用）");
        }
        userService.changeStatus(id, status);
        return R.ok();
    }

    @Operation(summary = "重置口令")
    @PostMapping("/{id}/password")
    @RequirePermission("system:user:update")
    public R<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String password = body.get("password");
        if (password == null || password.length() < 6) {
            return R.fail(com.lianzhi.attendance.common.result.ResultCode.PARAM_ERROR, "新密码长度至少 6 位");
        }
        userService.resetPassword(id, password);
        return R.ok();
    }
}
