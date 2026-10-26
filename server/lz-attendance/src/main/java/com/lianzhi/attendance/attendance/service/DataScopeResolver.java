package com.lianzhi.attendance.attendance.service;

import com.lianzhi.attendance.attendance.mapper.AttSupportMapper;
import com.lianzhi.attendance.framework.auth.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 数据范围判定：决定一个账号能看到谁的考勤。
 *
 * <p>考勤记录和考勤统计是两套查询，但"谁能看谁的数据"必须是同一套口径——
 * 否则会出现"记录接口只能看本部门，统计接口却能看到全公司"的越权漏洞。
 * 因此把判定抽到这里，两个服务共用。</p>
 *
 * <p>当前用户上下文只带权限码不带角色码，这里以「账号管理」权限作为系统管理员标识：
 * 该权限只有 ADMIN 持有（见 docs/sql/patch-phase2-permission.sql 的授权口径）。
 * 若后续 AuthUser 引入角色码字段，应改为显式判断角色而不是借道权限码。</p>
 */
@Service
@RequiredArgsConstructor
public class DataScopeResolver {

    public static final String SCOPE_ALL = "ALL";
    public static final String SCOPE_DEPT = "DEPT";
    public static final String SCOPE_SELF = "SELF";

    private static final String FULL_SCOPE_PERMISSION = "system:user:list";

    private final AttSupportMapper supportMapper;

    public String resolve(AuthUser user) {
        if (user != null && user.hasPermission(FULL_SCOPE_PERMISSION)) {
            return SCOPE_ALL;
        }
        if (deptIdOf(user) != null) {
            return SCOPE_DEPT;
        }
        return SCOPE_SELF;
    }

    /** 部门主管看到的部门 ID；管理员返回 null 表示不限 */
    public Long deptIdOf(AuthUser user) {
        if (user == null || user.getEmployeeId() == null) {
            return null;
        }
        return supportMapper.selectEmployeeDeptId(user.getEmployeeId());
    }

    /** 只能看自己时锁定的员工 ID */
    public Long selfEmployeeId(AuthUser user) {
        return user == null ? null : user.getEmployeeId();
    }
}
