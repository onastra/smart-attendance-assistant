package com.lianzhi.attendance.attendance.dto;

import lombok.Data;

/**
 * 人均工时趋势点（需求书第 12 天课程："分析员工考勤，员工工时分析"）。
 */
@Data
public class WorkHourPoint {

    /** 日期 yyyy-MM-dd */
    private String date;

    /** 当日人均工时（小时，按 work_minutes>0 的行平均） */
    private double avgHours;

    /** 当日有工时记录的人数 */
    private int coveredEmployees;
}
