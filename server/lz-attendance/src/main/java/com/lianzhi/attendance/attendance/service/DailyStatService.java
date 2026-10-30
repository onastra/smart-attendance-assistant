package com.lianzhi.attendance.attendance.service;

import com.lianzhi.attendance.attendance.dto.RebuildResult;
import com.lianzhi.attendance.attendance.entity.AttDailyStat;
import com.lianzhi.attendance.attendance.entity.AttWorkConfig;
import com.lianzhi.attendance.attendance.entity.DailyStatRow;
import com.lianzhi.attendance.attendance.mapper.AttStatMapper;
import com.lianzhi.attendance.framework.cache.CacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 考勤日汇总重算。
 *
 * <p>核心要求只有一条：<b>幂等</b>。同一天跑一次和跑十次，att_daily_stat 的内容必须完全一样。
 * 落在实现上是三件事——唯一键 upsert、状态判定只依赖流水（不依赖当前时间）、
 * 重算前清理已不在职员工的残留行。</p>
 *
 * <p>状态判定口径（与打卡时的即时判定保持一致，都经由 WorkConfigService 读取同一份全局配置）：
 * 已批请假 LEAVE（无打卡且覆盖请假单）→ 休息日 REST → 无打卡 ABSENT →
 * 上班晚于宽限 LATE → 下班早于下班时间 EARLY → 否则 NORMAL。
 * 迟到优先于早退：一天只有一个 status 字段，两者同时发生时记更严重的迟到。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyStatService {

    /** 单次重算的日期区间上限，防止误传一个很大的范围把库跑死 */
    private static final int MAX_RANGE_DAYS = 400;

    private final AttStatMapper statMapper;
    private final WorkConfigService workConfigService;
    private final CacheService cacheService;

    /**
     * 重算一整天。
     *
     * @param date 统计日期（不含时分秒）
     */
    @Transactional(rollbackFor = Exception.class)
    public RebuildResult rebuild(LocalDate date) {
        long start = System.currentTimeMillis();
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();

        List<DailyStatRow> rows = statMapper.selectDailyRows(from, to);
        boolean weekend = isWeekend(date);
        // 迟到/早退边界取全局配置（与打卡即时判定同源），一次重算只读一次
        AttWorkConfig workConfig = workConfigService.get();
        // E-07 审批联动：当天覆盖了已批请假的员工集合（当天无打卡时状态记 LEAVE 而不是 ABSENT）
        java.util.Set<Long> leaveEmployeeIds = new java.util.HashSet<>(statMapper.selectLeaveEmployeeIds(from, to));

        int sampleRows = 0;
        int absentRows = 0;

        for (DailyStatRow row : rows) {
            AttDailyStat stat = buildStat(row, date, weekend, leaveEmployeeIds, workConfig);
            statMapper.upsertStat(stat);
            if (AttDailyStat.SOURCE_SAMPLE.equals(stat.getSource())) {
                sampleRows++;
            }
            if (AttDailyStat.STATUS_ABSENT.equals(stat.getStatus())) {
                absentRows++;
            }
        }

        /*
         * 影响行数取"覆盖的员工数"，而不是累加 upsert 的返回值。
         * MySQL 的 ON DUPLICATE KEY UPDATE 返回值有三态：新插入返回 1、
         * 命中更新且值有变化返回 2、值完全没变返回 0。用它当影响行数的话，
         * 第一次重算和第二次重算的数字会不一样（实测 168 / 90），
         * 看起来像幂等被破坏了，实际只是 MySQL 的返回值语义如此。
         */
        int affected = rows.size();

        // 清理离职员工的历史汇总：他们已不在在职列表里，不会被上面的 upsert 覆盖
        statMapper.deleteInactiveEmployeeStat(date);

        RebuildResult result = new RebuildResult();
        result.setDate(date.toString());
        result.setEmployeeCount(rows.size());
        result.setAffectedRows(affected);
        result.setSampleRows(sampleRows);
        result.setAbsentRows(absentRows);
        result.setCostMillis(System.currentTimeMillis() - start);
        // 日汇总变了，统计看板的缓存必须立刻失效：
        // 否则管理员点了"重算"，看板在 TTL 内仍显示旧数字，
        // 会被当成"重算没生效"，比没有缓存更容易误导。
        cacheService.evictByPrefix("statistics:");
        return result;
    }

    /**
     * 重算一个日期区间，返回逐日结果。
     *
     * <p>补录了 14 天演示数据后需要一次性把历史全部算出来，逐日重算而不是一个大 SQL，
     * 是为了让每一天的判定都走同一段逻辑（含周末判断），避免"批量口径"和"单日口径"不一致。</p>
     */
    public List<RebuildResult> rebuildRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("日期范围不能为空");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("结束日期不能早于开始日期");
        }
        long days = Duration.between(from.atStartOfDay(), to.plusDays(1).atStartOfDay()).toDays();
        if (days > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("单次重算不能超过 " + MAX_RANGE_DAYS + " 天，当前为 " + days + " 天");
        }
        return from.datesUntil(to.plusDays(1))
                .map(this::rebuild)
                .collect(Collectors.toList());
    }

    private AttDailyStat buildStat(DailyStatRow row, LocalDate date, boolean weekend,
                                   java.util.Set<Long> leaveEmployeeIds, AttWorkConfig workConfig) {
        AttDailyStat stat = new AttDailyStat();
        stat.setEmployeeId(row.getEmployeeId());
        stat.setStatDate(date);
        stat.setFirstCheckin(row.getFirstCheckin());
        stat.setLastCheckout(row.getLastCheckout());
        stat.setRebuiltAt(LocalDateTime.now());
        stat.setCheckinCount((int) row.getRecordCount());

        long recordCount = row.getRecordCount();
        if (recordCount == 0) {
            /*
             * E-07 审批联动：无打卡时优先看请假——
             * 当天被已批请假单覆盖 → LEAVE（请假不算缺勤，也不算出勤）；
             * 请假单与打卡并存时以打卡为准（上了半天班的实际情况比请假标记更有信息量）。
             */
            if (leaveEmployeeIds.contains(row.getEmployeeId())) {
                stat.setWorkMinutes(0);
                stat.setStatus(AttDailyStat.STATUS_LEAVE);
                stat.setSource(AttDailyStat.SOURCE_REAL);
                return stat;
            }
            // 没来就是没来：休息日算 REST，工作日算 ABSENT，工时 0
            stat.setWorkMinutes(0);
            stat.setStatus(weekend ? AttDailyStat.STATUS_REST : AttDailyStat.STATUS_ABSENT);
            stat.setSource(AttDailyStat.SOURCE_REAL);
            return stat;
        }

        // 只要这一天里掺了演示数据就整体标 SAMPLE：宁可标脏，也不能把补录数据伪装成真实统计
        stat.setSource(row.getSampleCount() > 0 ? AttDailyStat.SOURCE_SAMPLE : AttDailyStat.SOURCE_REAL);
        stat.setWorkMinutes(calcWorkMinutes(row));

        if (weekend) {
            stat.setStatus(AttDailyStat.STATUS_REST);
            return stat;
        }

        boolean late = isLate(row.getFirstCheckin(), workConfig);
        boolean early = isEarly(row.getLastCheckout(), workConfig);
        if (late) {
            stat.setStatus(AttDailyStat.STATUS_LATE);
        } else if (early) {
            stat.setStatus(AttDailyStat.STATUS_EARLY);
        } else {
            stat.setStatus(AttDailyStat.STATUS_NORMAL);
        }
        return stat;
    }

    /**
     * 工时（分钟）：下班减上班。
     *
     * <p>只有上班卡或只有下班卡时记 0 而不是负数——漏打卡是常见情况，
     * 算出 -480 分钟会让平均工时统计失真。</p>
     */
    private int calcWorkMinutes(DailyStatRow row) {
        if (row.getFirstCheckin() == null || !row.hasCheckout()) {
            return 0;
        }
        long minutes = Duration.between(row.getFirstCheckin(), row.getLastCheckout()).toMinutes();
        return minutes > 0 ? (int) minutes : 0;
    }

    private boolean isLate(LocalDateTime firstCheckin, AttWorkConfig config) {
        if (firstCheckin == null) {
            return false;
        }
        LocalTime limit = config.getWorkStartTime().plusMinutes(config.getLateGraceMinutes());
        return firstCheckin.toLocalTime().isAfter(limit);
    }

    private boolean isEarly(LocalDateTime lastCheckout, AttWorkConfig config) {
        if (lastCheckout == null) {
            return false;
        }
        LocalTime limit = config.getWorkEndTime().minusMinutes(config.getEarlyLeaveGraceMinutes());
        return lastCheckout.toLocalTime().isBefore(limit);
    }

    private boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    /** 汇总表当前覆盖的天数，用于看板提示"数据范围" */
    public int statDays() {
        return statMapper.countStatDays();
    }

    /**
     * 最早一条打卡流水的日期；没有任何流水时返回 null。
     *
     * <p>不带参数的重算用它来推导区间：首次部署时演示流水落在过去若干天，
     * 若只重算今天，<code>att_daily_stat</code> 几乎为空，看板会显示全 0，
     * 看起来就像统计模块没实现。</p>
     */
    public LocalDate earliestRecordDate() {
        return statMapper.selectEarliestRecordDate();
    }
}
