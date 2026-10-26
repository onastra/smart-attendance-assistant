package com.lianzhi.attendance.attendance.dto;

import lombok.Data;

/**
 * 部门维度对比：看板柱状图的数据源。
 */
@Data
public class DeptStatItem {

    private Long deptId;
    private String deptName;

    /** 部门在职员工数（统计范围内的去重人数） */
    private int employeeCount;

    private long checkinCount;
    private long normalDays;
    private long lateDays;
    private long earlyDays;
    private long absentDays;

    private double attendanceRate;
    private int avgWorkMinutes;
}
