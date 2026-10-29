package com.lianzhi.attendance.attendance.dto;

import lombok.Data;

/**
 * 今日未打卡/请假员工（需求书数据中心："整体看板，员工考勤实时数据报表，离岗员工数据分布"）。
 */
@Data
public class AbsentEmployee {

    public static final String STATUS_ABSENT = "ABSENT";
    public static final String STATUS_LEAVE = "LEAVE";

    private Long employeeId;
    private String employeeName;
    private String deptName;

    /** ABSENT=今日未打卡；LEAVE=今日已批请假（合规离岗） */
    private String status;
}
