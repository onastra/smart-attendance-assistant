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
     * 数据范围判定委托给 {@link DataScopeResolver}。
     *
     * <p>阶段 4 起统计接口也用同一套判定：如果两处各写一份，
     * 迟早会出现"记录接口只能看本部门、统计接口能看到全公司"的越权漏洞。</p>
     */
    private final DataScopeResolver dataScopeResolver;

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
        return dataScopeResolver.resolve(user);
    }

    private void applyDataScope(AttRecordQuery query, AuthUser user) {
        String scope = dataScopeResolver.resolve(user);
        if (DataScopeResolver.SCOPE_ALL.equals(scope)) {
            return;
        }
        if (DataScopeResolver.SCOPE_DEPT.equals(scope)) {
            // 部门主管：无论前端怎么传，结果都被限制在本部门内
            query.setDeptId(dataScopeResolver.deptIdOf(user));
            return;
        }
        // 既不是管理员、又没有部门归属（例如账号尚未建档）：只能看自己的
        query.setEmployeeId(dataScopeResolver.selfEmployeeId(user));
    }

    private PageResult<AttRecordView> doQuery(AttRecordQuery query) {
        long total = recordMapper.countByQuery(query);
        if (total == 0) {
            return PageResult.empty(query);
        }
        return PageResult.of(query, total, recordMapper.selectByQuery(query));
    }
}
