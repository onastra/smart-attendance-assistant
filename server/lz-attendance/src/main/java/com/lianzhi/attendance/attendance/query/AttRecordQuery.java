package com.lianzhi.attendance.attendance.query;

import com.lianzhi.attendance.common.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 考勤记录查询条件（管理员端多条件筛选）。
 *
 * <p>本人查询复用同一对象，但 employeeId 由服务端强制写入当前登录用户的员工 ID，
 * 不接受前端传入——否则普通员工改一个参数就能查到别人的考勤。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AttRecordQuery extends PageQuery {

    private Long employeeId;

    /** 员工姓名或工号模糊匹配 */
    private String keyword;

    private Long deptId;
    private Long placeId;

    /** ON_DUTY / OFF_DUTY / FIELD */
    private String checkinType;

    /** NORMAL / LATE / EARLY / ABNORMAL */
    private String result;

    /** 起始日期（含），格式 yyyy-MM-dd */
    private String dateFrom;

    /** 截止日期（含），格式 yyyy-MM-dd */
    private String dateTo;
}
