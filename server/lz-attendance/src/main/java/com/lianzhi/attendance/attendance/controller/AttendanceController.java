package com.lianzhi.attendance.attendance.controller;

import com.lianzhi.attendance.attendance.entity.AttRecordView;
import com.lianzhi.attendance.attendance.query.AttRecordQuery;
import com.lianzhi.attendance.attendance.service.AttendanceService;
import com.lianzhi.attendance.common.exception.BusinessException;
import com.lianzhi.attendance.common.page.PageResult;
import com.lianzhi.attendance.common.result.R;
import com.lianzhi.attendance.common.result.ResultCode;
import com.lianzhi.attendance.framework.auth.AuthContext;
import com.lianzhi.attendance.framework.auth.AuthUser;
import com.lianzhi.attendance.framework.auth.RequireLogin;
import com.lianzhi.attendance.framework.auth.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 考勤记录查询：本人链路与管理员链路。
 */
@Tag(name = "考勤记录", description = "本人考勤查询与管理员多条件查询")
@Validated
@RestController
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    @Operation(summary = "我的考勤记录", description = "只能查本人；越权传 employeeId 不生效")
    @GetMapping("/api/my/attendance/records")
    @RequireLogin
    public R<Map<String, Object>> myRecords(AttRecordQuery query) {
        AuthUser user = AuthContext.get();
        if (user == null || user.getEmployeeId() == null) {
            throw new BusinessException(ResultCode.CONFLICT, "当前账号未关联员工档案，没有考勤记录");
        }
        PageResult<AttRecordView> page = attendanceService.myRecords(query, user.getEmployeeId());
        Map<String, Object> data = new HashMap<>();
        data.put("page", page);
        data.put("employeeId", user.getEmployeeId());
        data.put("dataScope", "SELF");
        return R.ok(data);
    }

    /**
     * 管理员考勤记录。
     *
     * <p>权限码用 <code>attendance:record:admin</code> 而不是本人查询用的
     * <code>attendance:record:query</code>：两者如果共用一个码，
     * 普通员工（EMPLOYEE 默认持有 query）就能直接调这个接口看到同部门同事的考勤。
     * 分开之后，普通员工调用本接口会返回 40301。</p>
     */
    @Operation(summary = "管理员考勤记录", description = "支持员工/部门/场所/类型/结果/日期组合筛选；数据范围按角色收敛")
    @GetMapping("/api/admin/attendance/records")
    @RequirePermission("attendance:record:admin")
    public R<Map<String, Object>> adminRecords(AttRecordQuery query) {
        AuthUser user = AuthContext.get();
        PageResult<AttRecordView> page = attendanceService.adminRecords(query, user);
        Map<String, Object> data = new HashMap<>();
        data.put("page", page);
        data.put("dataScope", attendanceService.dataScope(user));
        return R.ok(data);
    }
}
