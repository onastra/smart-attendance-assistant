package com.lianzhi.attendance.attendance.dto;

import lombok.Data;

/**
 * 统计概览：看板顶部那排数字。
 *
 * <p>字段说明刻意写得直白，因为这几个数是最容易被质疑口径的地方：
 * 出勤率的分母到底是"所有人天"还是"工作日人天"，直接影响数字大小。
 * 这里同时把分子分母都返回，前端可以自行核对。</p>
 */
@Data
public class StatSummary {

    private String dateFrom;
    private String dateTo;

    /** 覆盖员工数 */
    private int employeeCount;

    /** 覆盖天数（att_daily_stat 中去重后的日期数） */
    private int statDays;

    /** 人天数 = 员工数 × 天数，所有比率的基准 */
    private long personDays;

    /** 应出勤人天 = 人天数 - 休息日人天 */
    private long workdayPersonDays;

    private long normalDays;
    private long lateDays;
    private long earlyDays;
    private long absentDays;
    /** 请假人天（E-07：已批请假单覆盖且当天无打卡，不算缺勤也不算出勤） */
    private long leaveDays;
    private long restDays;

    /** 打卡总次数（来自 att_daily_stat.checkin_count 累加） */
    private long checkinCount;

    /** 平均工时（分钟），按有工时的行取平均 */
    private int avgWorkMinutes;

    /** 出勤率（百分比，保留一位小数）：出勤人天 / 应出勤人天 */
    private double attendanceRate;

    /** 数据来源：real / sample / mixed */
    private String dataSource;

    /** 范围内含演示数据的天数 */
    private long sampleDays;

    /** 数据来源的可读说明，前端直接展示 */
    private String dataSourceNote;

    /**
     * 本次结果是否来自缓存。
     *
     * <p>必须如实返回：看板数字若来自 60 秒内的缓存，使用者有权知道，
     * 否则刚打完卡发现看板没变会误以为数据丢了。</p>
     */
    private boolean cached;

    /** 结果生成时间（缓存命中时为写入缓存的时间），格式 yyyy-MM-dd HH:mm:ss */
    private String generatedAt;
}
