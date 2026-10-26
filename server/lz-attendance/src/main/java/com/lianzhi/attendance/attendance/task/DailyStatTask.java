package com.lianzhi.attendance.attendance.task;

import com.lianzhi.attendance.attendance.dto.RebuildResult;
import com.lianzhi.attendance.attendance.service.DailyStatService;
import com.lianzhi.attendance.attendance.service.JobLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 日汇总定时任务：每天凌晨重算「昨天」和「今天」。
 *
 * <p>为什么算两天：昨天的汇总在当天 23:59 之后才是完整数据（有人临近午夜才打下班卡），
 * 今天这趟则是让看板一早打开就有当天数据，不用等管理员手动点重算。</p>
 *
 * <p>幂等保证来自 {@link DailyStatService#rebuild} 的 upsert，重复执行不会多出行；
 * 每次执行都写 job_exec_log，出问题时能查到"跑没跑、影响几行"。</p>
 *
 * <p>cron 用 0 30 1 * * ?（每天 01:30）：避开 00:00 前后的跨日边界，
 * 也避开业务高峰。演示时若想立刻看到效果，用后台的「重新计算」按钮手动触发即可。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyStatTask {

    private static final String JOB_NAME = "DAILY_STAT_REBUILD";

    private final DailyStatService dailyStatService;
    private final JobLogService jobLogService;

    @Scheduled(cron = "0 30 1 * * ?")
    public void rebuildYesterdayAndToday() {
        LocalDateTime start = LocalDateTime.now();
        LocalDate today = LocalDate.now();
        try {
            RebuildResult yesterday = dailyStatService.rebuild(today.minusDays(1));
            RebuildResult todayResult = dailyStatService.rebuild(today);
            int rows = yesterday.getAffectedRows() + todayResult.getAffectedRows();
            log.info("[JOB] 日汇总重算完成 昨天={}行 今天={}行 耗时={}ms+{}ms",
                    yesterday.getAffectedRows(), todayResult.getAffectedRows(),
                    yesterday.getCostMillis(), todayResult.getCostMillis());
            jobLogService.log(JOB_NAME, start, LocalDateTime.now(), true, rows,
                    "yesterday=" + yesterday.getAffectedRows() + " today=" + todayResult.getAffectedRows());
        } catch (Exception e) {
            log.error("[JOB] 日汇总重算失败", e);
            jobLogService.log(JOB_NAME, start, LocalDateTime.now(), false, 0, e.getMessage());
        }
    }
}
