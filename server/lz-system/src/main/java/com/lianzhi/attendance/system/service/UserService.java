package com.lianzhi.attendance.system.service;

import com.lianzhi.attendance.common.exception.BusinessException;
import com.lianzhi.attendance.common.page.PageResult;
import com.lianzhi.attendance.common.result.ResultCode;
import com.lianzhi.attendance.system.dto.UserVO;
import com.lianzhi.attendance.system.entity.SysRole;
import com.lianzhi.attendance.system.entity.SysUser;
import com.lianzhi.attendance.system.mapper.SysRoleMapper;
import com.lianzhi.attendance.system.mapper.SysUserMapper;
import com.lianzhi.attendance.system.query.UserQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 账号管理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    /**
     * 与 AuthService 保持一致使用标准 BCrypt，避免同一个库里出现带前缀与不带前缀两种摘要格式。
     */
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;

    public PageResult<UserVO> page(UserQuery query) {
        long total = sysUserMapper.countByQuery(query);
        if (total == 0) {
            return PageResult.empty(query);
        }
        List<SysUser> rows = sysUserMapper.selectByQuery(query);
        return PageResult.of(query, total, rows.stream().map(this::toVO).collect(Collectors.toList()));
    }

    @Transactional
    public Long create(SysUser user, String rawPassword, List<Long> roleIds) {
        if (sysUserMapper.countByUsername(user.getUsername(), null) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "该用户名已存在");
        }
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        if (user.getStatus() == null) {
            user.setStatus(1);
        }
        sysUserMapper.insert(user);
        replaceRoles(user.getId(), roleIds);
        log.info("[USER] 新建账号 id={} username={} roleIds={}", user.getId(), user.getUsername(), roleIds);
        return user.getId();
    }

    @Transactional
    public void update(SysUser user, List<Long> roleIds) {
        requireUser(user.getId());
        if (user.getUsername() != null
                && sysUserMapper.countByUsername(user.getUsername(), user.getId()) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "该用户名已被其他账号占用");
        }
        // 口令不允许从这个入口修改，走独立的 resetPassword
        user.setPasswordHash(null);
        sysUserMapper.updateById(user);
        if (roleIds != null) {
            replaceRoles(user.getId(), roleIds);
        }
        log.info("[USER] 编辑账号 id={}", user.getId());
    }

    @Transactional
    public void changeStatus(Long id, Integer status) {
        requireUser(id);
        SysUser patch = new SysUser();
        patch.setId(id);
        patch.setStatus(status);
        sysUserMapper.updateById(patch);
        log.info("[USER] 变更账号状态 id={} status={}", id, status);
    }

    @Transactional
    public void resetPassword(Long id, String rawPassword) {
        requireUser(id);
        SysUser patch = new SysUser();
        patch.setId(id);
        patch.setPasswordHash(passwordEncoder.encode(rawPassword));
        sysUserMapper.updateById(patch);
        log.info("[USER] 重置口令 id={}", id);
    }

    public List<SysRole> listAllRoles() {
        return sysRoleMapper.selectAllRoles();
    }

    /**
     * 全量覆盖式授权：先清除旧关联，再写入本次传入的完整角色列表。
     *
     * <p>语义上"传空列表即清空角色"，调用方必须传全量而非增量。</p>
     */
    @Transactional
    void replaceRoles(Long userId, List<Long> roleIds) {
        sysUserMapper.deleteUserRoles(userId);
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        sysUserMapper.insertUserRoles(userId, roleIds);
    }

    private SysUser requireUser(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "账号不存在");
        }
        return user;
    }

    private UserVO toVO(SysUser user) {
        List<SysRole> roles = sysRoleMapper.selectRolesByUserId(user.getId());
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setEmployeeId(user.getEmployeeId());
        vo.setStatus(user.getStatus());
        vo.setLastLoginAt(user.getLastLoginAt());
        vo.setLastLoginIp(user.getLastLoginIp());
        vo.setRoles(roles.stream().map(SysRole::getRoleCode).collect(Collectors.toList()));
        vo.setRoleIds(roles.stream().map(SysRole::getId).collect(Collectors.toList()));
        return vo;
    }
}
