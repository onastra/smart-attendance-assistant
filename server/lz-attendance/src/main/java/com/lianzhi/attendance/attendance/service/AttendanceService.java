package com.lianzhi.attendance.attendance.service;

import com.lianzhi.attendance.attendance.entity.AttRecordView;
import com.lianzhi.attendance.attendance.mapper.AttRecordMapper;
import com.lianzhi.attendance.attendance.mapper.AttSupportMapper;
import com.lianzhi.attendance.attendance.query.AttRecordQuery;
import com.lianzhi.attendance.common.page.PageResult;
import com.lianzhi.attendance.framework.auth.AuthUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 考勤记录查询。
 *
 * <p>两条链路共用一套 SQL，区别只在数据范围：
 * 本人查询强制写入当前登录用户的员工 ID，管理员查询按角色收敛到全部/本部门/本人。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceService {

    /**
     * 判断是否具有全量数据范围。
     *
     * <p>当前用户上下文只带权限码不带角色码，这里以「账号管理」权限作为系统管理员标识：
     * 该权限只有 ADMIN 角色持有（见 docs/sql/patch-phase2-permission.sql 的授权口径）。
     * 若后续引入角色码字段，应改为显式判断角色而不是借道权限码。</p>
     */
    private static final String FULL_SCOPE_PERMISSION = "system:user:list";

    private final AttRecordMapper recordMapper;
    private final AttSupportMapper supportMapper;

    public PageResult<AttRecordView> myRecords(AttRecordQuery query, Long employeeId) {
        // 强制覆盖：即便前端传了 employeeId 也不生效，避免普通员工越权查看他人考勤
        query.setEmployeeId(employeeId);
        return doQuery(query);
    }

    public PageResult<AttRecordView> adminRecords(AttRecordQuery query, AuthUser user) {
        applyDataScope(query, user);
        return doQuery(query);
    }

    /**
     * 管理员可查询的范围说明，用于在接口响应中如实告知前端"你看到的是哪个范围的数据"。
     */
    public String dataScope(AuthUser user) {
        if (user != null && user.hasPermission(FULL_SCOPE_PERMISSION)) {
            return "ALL";
        }
        Long deptId = deptIdOf(user);
        if (deptId != null) {
            return "DEPT";
        }
        return "SELF";
    }

    private void applyDataScope(AttRecordQuery query, AuthUser user) {
        if (user != null && user.hasPermission(FULL_SCOPE_PERMISSION)) {
            return;
        }
        Long deptId = deptIdOf(user);
        if (deptId != null) {
            // 部门主管：无论前端怎么传，结果都被限制在本部门内
            query.setDeptId(deptId);
            return;
        }
        // 既不是管理员、又没有部门归属（例如账号尚未建档）：只能看自己的
        query.setEmployeeId(user == null ? null : user.getEmployeeId());
    }

    private Long deptIdOf(AuthUser user) {
        if (user == null || user.getEmployeeId() == null) {
            return null;
        }
        return supportMapper.selectEmployeeDeptId(user.getEmployeeId());
    }

    private PageResult<AttRecordView> doQuery(AttRecordQuery query) {
        long total = recordMapper.countByQuery(query);
        if (total == 0) {
            return PageResult.empty(query);
        }
        return PageResult.of(query, total, recordMapper.selectByQuery(query));
    }
}
