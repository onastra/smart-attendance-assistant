package com.lianzhi.attendance.attendance.mapper;

import com.lianzhi.attendance.attendance.entity.AttDailyStat;
import com.lianzhi.attendance.attendance.entity.DailyStatRow;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 考勤日汇总的数据访问。
 *
 * <p>聚合口径都在 SQL 里完成（而不是把流水捞到内存里算）：
 * 数据量上来后内存聚合会把一次统计变成一次全表捞取，这里只让数据库返回
 * 每人每天一行的结果。</p>
 */
public interface AttStatMapper {

    /**
     * 聚合某一天的打卡流水。
     *
     * <p>LEFT JOIN 在职员工表是刻意的：当天没人打卡的员工也必须出现在结果里，
     * 否则"缺勤"这个统计项永远是 0（没来的人不会自己生成一条记录）。</p>
     *
     * @param from 日期起点（含，yyyy-MM-dd 00:00:00）
     * @param to   日期终点（不含，次日 00:00:00）
     */
    List<DailyStatRow> selectDailyRows(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * 查询某时间段内已通过请假的员工 ID（E-07 审批联动）。
     *
     * <p>直接读 biz_application 表（同一数据库，跨模块表级只读），
     * 不引入 lz-approval 的类依赖，保持模块单向依赖。</p>
     *
     * @param from 日期起点（含）
     * @param to   日期终点（不含）
     */
    List<Long> selectLeaveEmployeeIds(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * 写入或更新一行汇总。
     *
     * <p>依赖 <code>uk_employee_date</code> 唯一键：重复重算同一天不会产生第二行，
     * 只会把已有行刷新，这是"幂等"在存储层的落点。</p>
     */
    int upsertStat(AttDailyStat stat);

    /**
     * 删除指定日期内已不在职员工的汇总行。
     *
     * <p>场景：某员工离职后，历史日期里还留着他之前的汇总。重算当天时这些行
     * 不会被 upsert 覆盖（他已经不在在职列表里），需要显式清理，否则看板上
     * 会出现查不到姓名的人头。</p>
     */
    int deleteInactiveEmployeeStat(@Param("statDate") LocalDate statDate);

    /** 汇总覆盖的日期范围，用于告诉调用方"看板现在有几天数据" */
    int countStatDays();

    /**
     * 最早一条打卡流水的日期；一条记录都没有时返回 null。
     *
     * <p>用于不带参数的重算自动推导区间，而不是机械地只算今天。</p>
     */
    LocalDate selectEarliestRecordDate();

    /** 定时任务执行日志：每次重算留痕，便于排查"今天的汇总到底跑没跑" */
    int insertJobLog(@Param("jobName") String jobName,
                     @Param("startTime") LocalDateTime startTime,
                     @Param("endTime") LocalDateTime endTime,
                     @Param("success") int success,
                     @Param("affectedRows") Integer affectedRows,
                     @Param("message") String message);
}
