package com.lianzhi.attendance.attendance.dto;

import lombok.Data;

/**
 * 趋势图的一个点：某个时间粒度（日/周/月）下的汇总。
 */
@Data
public class TrendPoint {

    /** 日：2026-10-12；周：2026-W42；月：2026-10 */
    private String period;

    private long checkinCount;
    private long normalDays;
    private long lateDays;
    private long earlyDays;
    private long absentDays;

    /** 出勤率（百分比，一位小数） */
    private double attendanceRate;

    private int avgWorkMinutes;

    /** 该点是否含演示数据，前端据此打标签 */
    private boolean sample;
}
