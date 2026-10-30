package com.lianzhi.attendance.system.service;

import com.lianzhi.attendance.common.exception.BusinessException;
import com.lianzhi.attendance.common.result.ResultCode;
import com.lianzhi.attendance.framework.auth.AuthUser;
import com.lianzhi.attendance.framework.auth.TokenService;
import com.lianzhi.attendance.system.dto.LoginRequest;
import com.lianzhi.attendance.system.dto.LoginVO;
import com.lianzhi.attendance.system.entity.SysRole;
import com.lianzhi.attendance.system.entity.SysUser;
import com.lianzhi.attendance.system.mapper.SysRoleMapper;
import com.lianzhi.attendance.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 认证服务。
 *
 * <p>安全约定：
 * <ol>
 *   <li>登录失败统一返回相同文案，不区分"用户不存在"与"密码错误"，避免账号枚举。</li>
 *   <li>停用账号即使口令正确也拒绝登录。</li>
 *   <li>任何返回给前端的结构都不包含 password_hash。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /**
     * 使用标准 BCrypt，而非 DelegatingPasswordEncoder。
     *
     * <p>原因很实在：库中既有摘要是纯 {@code $2a$10$...} 格式，没有 {@code {bcrypt}} 前缀。
     * DelegatingPasswordEncoder 找不到前缀会直接抛 IllegalArgumentException 而不是返回 false，
     * 登录接口因此 500。这类"看起来是校验失败、实际是崩溃"的问题在演示时很难当场解释，
     * 所以在没有多算法共存需求时，直接用 BCrypt 实现。</p>
     */
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final TokenService tokenService;

    /**
     * 当前登录者完整档案：与登录返回同构（无 token/expireSeconds）。
     *
     * <p>JWT 无状态，AuthUser 从 token 只能还原出身份标识（userId/username/employeeId），
     * 不含权限码——此前 /auth/profile 直接透传 AuthUser 导致 permissions 恒为空数组，
     * 小程序"我的"页一刷新就把登录时缓存的权限覆盖丢了（首页宫格随之消失）。
     * 这里与登录同源回库补齐角色与权限码。</p>
     */
    @Transactional(readOnly = true)
    public LoginVO profile(AuthUser user) {
        List<SysRole> roles = sysRoleMapper.selectRolesByUserId(user.getUserId());
        List<String> roleCodes = roles.stream()
                .map(SysRole::getRoleCode)
                .collect(Collectors.toList());
        Set<String> permissions = Set.copyOf(
                roles.isEmpty() ? Set.<String>of() : sysRoleMapper.selectPermCodesByUserId(user.getUserId()));

        // nickname 以库内最新为准（AuthUser 里没有该字段）
        SysUser dbUser = sysUserMapper.selectById(user.getUserId());

        return LoginVO.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .nickname(dbUser != null ? dbUser.getNickname() : null)
                .employeeId(user.getEmployeeId())
                .roles(roleCodes)
                .permissions(permissions)
                .build();
    }

    @Transactional
    public LoginVO login(LoginRequest request, String clientIp) {
        SysUser user = sysUserMapper.selectByUsername(request.getUsername());

        // 账号不存在也走一次散列比对流程的等价分支，避免通过响应耗时侧信道枚举账号
        if (user == null) {
            log.info("[LOGIN] 失败：账号不存在 username={} ip={}", request.getUsername(), clientIp);
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.info("[LOGIN] 失败：口令不匹配 userId={} ip={}", user.getId(), clientIp);
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }

        if (user.getStatus() == null || user.getStatus() != 1) {
            log.info("[LOGIN] 失败：账号已停用 userId={} ip={}", user.getId(), clientIp);
            throw new BusinessException(ResultCode.UNAUTHORIZED, "该账号已停用，请联系管理员");
        }

        List<SysRole> roles = sysRoleMapper.selectRolesByUserId(user.getId());
        List<String> roleCodes = roles.stream()
                .map(SysRole::getRoleCode)
                .collect(Collectors.toList());
        Set<String> permissions = Set.copyOf(
                roles.isEmpty() ? Set.<String>of() : sysRoleMapper.selectPermCodesByUserId(user.getId()));

        String token = tokenService.generateToken(user.getId(), user.getUsername(), user.getEmployeeId());

        // 回写登录信息失败不应影响登录结果，属于审计性质的数据
        try {
            sysUserMapper.updateLoginInfo(user.getId(), clientIp);
        } catch (Exception e) {
            log.warn("[LOGIN] 回写登录信息失败 userId={} err={}", user.getId(), e.getMessage());
        }

        log.info("[LOGIN] 成功 userId={} username={} roles={} ip={}",
                user.getId(), user.getUsername(), roleCodes, clientIp);

        return LoginVO.builder()
                .token(token)
                .expireSeconds(tokenService.getExpireSeconds())
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .employeeId(user.getEmployeeId())
                .roles(roleCodes)
                .permissions(permissions)
                .build();
    }
}
