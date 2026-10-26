package com.lianzhi.attendance.system.auth;

import com.lianzhi.attendance.framework.auth.PermissionChecker;
import com.lianzhi.attendance.system.mapper.SysRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;

/**
 * 权限数据来源实现。
 *
 * <p>框架层只定义 {@link PermissionChecker} 契约，这里由 <code>lz-system</code> 落地，
 * 避免 <code>lz-framework</code> 反向依赖业务模块。</p>
 *
 * <p>当前每次请求都查库（三表联查），没有引入进程内缓存：
 * 一是演示环境下数据量极小，二是权限变更后能立即生效——做缓存就要处理失效一致性，
 * 在没测透之前引入缓存属于用正确性换性能，不划算。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionCheckerImpl implements PermissionChecker {

    private final SysRoleMapper sysRoleMapper;

    @Override
    public Set<String> permissionsOf(Long userId) {
        if (userId == null) {
            return Collections.emptySet();
        }
        Set<String> codes = sysRoleMapper.selectPermCodesByUserId(userId);
        return codes == null ? Collections.emptySet() : codes;
    }
}
