package com.lianzhi.attendance.attendance.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 考勤日汇总：每人每天一行，由定时任务或手动重算生成。
 *
 * <p>为什么需要这张表：看板的趋势图要按天/周/月聚合，如果每次都扫 att_record
 * 现算，14 天 × 5 人还能扛，数据量上来后每次打开看板都是一次全表扫描。
 * 汇总表的价值在于"算一次、查多次"，代价是要保证它能被重算且重算结果一致（幂等）。</p>
 *
 * <p>幂等前提：<code>uk_employee_date</code> 唯一键 —— 同一员工同一天永远只有一行，
 * 重算走 <code>ON DUPLICATE KEY UPDATE</code>，跑几次结果都一样。</p>
 */
@Data
public class AttDailyStat {

    /** 正常出勤 */
    public static final String STATUS_NORMAL = "NORMAL";
    /** 迟到 */
    public static final String STATUS_LATE = "LATE";
    /** 早退 */
    public static final String STATUS_EARLY = "EARLY";
    /** 缺勤（工作日无任何打卡） */
    public static final String STATUS_ABSENT = "ABSENT";
    /** 已批请假（E-07：当天无打卡但覆盖了已通过的请假单，不算缺勤） */
    public static final String STATUS_LEAVE = "LEAVE";
    /** 休息日 */
    public static final String STATUS_REST = "REST";

    /** 数据来源：真实聚合 */
    public static final String SOURCE_REAL = "REAL";
    /** 数据来源：演示补录数据 */
    public static final String SOURCE_SAMPLE = "SAMPLE";

    private Long id;
    private Long employeeId;
    private LocalDate statDate;
    private LocalDateTime firstCheckin;
    private LocalDateTime lastCheckout;
    private int workMinutes;

    /** 当日打卡次数：趋势图要看"人次"，不能只靠工时反推 */
    private int checkinCount;

    private String status;
    private String source;
    private LocalDateTime rebuiltAt;

    /** 冗余字段：员工姓名与部门，供看板直接展示，避免二次关联 */
    private String employeeName;
    private Long deptId;
    private String deptName;
}
