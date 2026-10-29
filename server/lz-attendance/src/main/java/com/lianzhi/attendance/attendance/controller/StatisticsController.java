package com.lianzhi.attendance.attendance.controller;

import com.lianzhi.attendance.attendance.dto.DeptStatItem;
import com.lianzhi.attendance.attendance.dto.PlaceHourItem;
import com.lianzhi.attendance.attendance.dto.RebuildResult;
import com.lianzhi.attendance.attendance.dto.StatSummary;
import com.lianzhi.attendance.attendance.dto.TrendPoint;
import com.lianzhi.attendance.attendance.query.StatisticsQuery;
import com.lianzhi.attendance.attendance.service.DailyStatService;
import com.lianzhi.attendance.attendance.service.DataScopeResolver;
import com.lianzhi.attendance.attendance.service.JobLogService;
import com.lianzhi.attendance.attendance.service.StatisticsService;
import com.lianzhi.attendance.common.exception.BusinessException;
import com.lianzhi.attendance.common.result.R;
import com.lianzhi.attendance.common.result.ResultCode;
import com.lianzhi.attendance.framework.auth.AuthContext;
import com.lianzhi.attendance.framework.auth.AuthUser;
import com.lianzhi.attendance.framework.auth.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计与看板接口。
 *
 * <p>查看类接口统一用 <code>statistics:view</code>（ADMIN / MANAGER）；
 * 重算类接口单独用 <code>statistics:rebuild</code>（仅 ADMIN）——
 * 重算会改写 att_daily_stat，是写操作，不能让所有能看看板的人触发。</p>
 */
@Slf4j
@Tag(name = "统计与看板", description = "汇总概览、趋势、部门对比、场所时段分布与日汇总重算")
@RestController
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;
    private final DailyStatService dailyStatService;
    private final JobLogService jobLogService;
    private final DataScopeResolver dataScopeResolver;

    @Operation(summary = "统计概览", description = "出勤率/迟到/早退/缺勤/平均工时，响应带 dataSource 说明数据来源")
    @GetMapping("/api/statistics/summary")
    @RequirePermission("statistics:view")
    public R<Map<String, Object>> summary(StatisticsQuery query) {
        AuthUser user = AuthContext.get();
        StatSummary summary = statisticsService.summary(query, user);
        Map<String, Object> data = new HashMap<>();
        data.put("summary", summary);
        data.put("dataScope", describeScope(user));
        return R.ok(data);
    }

    @Operation(summary = "趋势数据", description = "type=day|week|month，供 ECharts 折线/柱状图使用")
    @GetMapping("/api/statistics/trend")
    @RequirePermission("statistics:view")
    public R<Map<String, Object>> trend(StatisticsQuery query) {
        AuthUser user = AuthContext.get();
        List<TrendPoint> points = statisticsService.trend(query, user);
        Map<String, Object> data = new HashMap<>();
        data.put("points", points);
        data.put("type", query.getType());
        data.put("dataScope", describeScope(user));
        data.put("empty", points.isEmpty());
        return R.ok(data);
    }

    @Operation(summary = "部门对比", description = "按部门汇总打卡次数、迟到早退与出勤率")
    @GetMapping("/api/statistics/department")
    @RequirePermission("statistics:view")
    public R<Map<String, Object>> department(StatisticsQuery query) {
        AuthUser user = AuthContext.get();
        List<DeptStatItem> items = statisticsService.department(query, user);
        Map<String, Object> data = new HashMap<>();
        data.put("items", items);
        data.put("dataScope", describeScope(user));
        return R.ok(data);
    }

    @Operation(summary = "场所时段分布", description = "按场所 × 小时统计打卡次数；无地图 Key 时返回时段数据而非地图图层")
    @GetMapping("/api/statistics/place-heat")
    @RequirePermission("statistics:view")
    public R<Map<String, Object>> placeHeat(StatisticsQuery query) {
        AuthUser user = AuthContext.get();
        Map<String, Object> heat = statisticsService.placeHeat(query, user);
        heat.put("dataScope", describeScope(user));
        return R.ok(heat);
    }

    @Operation(summary = "出勤趋势预测", description = "近 28 天同星期均值外推未来 7 天出勤率（历史为实线，未来为预测）")
    @GetMapping("/api/statistics/forecast")
    @RequirePermission("statistics:view")
    public R<Map<String, Object>> forecast() {
        return R.ok(statisticsService.forecast(AuthContext.get()));
    }

    @Operation(summary = "今日未打卡/请假名单", description = "在职员工逐人判定：已打卡不返回；覆盖已批请假记 LEAVE；其余记 ABSENT")
    @GetMapping("/api/statistics/today-absent")
    @RequirePermission("statistics:view")
    public R<Map<String, Object>> todayAbsent() {
        return R.ok(statisticsService.todayAbsent(AuthContext.get()));
    }

    @Operation(summary = "人均工时趋势", description = "按日的人均工时（小时）与有工时人数，供工时分析")
    @GetMapping("/api/statistics/work-hours")
    @RequirePermission("statistics:view")
    public R<Map<String, Object>> workHours(StatisticsQuery query) {
        AuthUser user = AuthContext.get();
        List<com.lianzhi.attendance.attendance.dto.WorkHourPoint> points =
                statisticsService.workHours(query, user);
        Map<String, Object> data = new HashMap<>();
        data.put("points", points);
        data.put("dataScope", describeScope(user));
        return R.ok(data);
    }

    @Operation(summary = "员工画像", description = "近 30 天出勤构成与每日出勤率；SELF 范围强制本人，DEPT 范围限本部门")
    @GetMapping("/api/statistics/employee-profile")
    @RequirePermission("statistics:view")
    public R<Map<String, Object>> employeeProfile(StatisticsQuery query) {
        return R.ok(statisticsService.employeeProfile(query, AuthContext.get()));
    }

    /**
     * 手动重算日汇总。
     *
     * <p>不传 date 时重算今天；传了则重算指定日。幂等：同一天跑多次结果一致。
     * 补录演示数据后需要一次性把历史算出来时，用 dateFrom / dateTo 指定区间。</p>
     */
    @Operation(summary = "重算日汇总", description = "幂等：同一天重复执行结果一致；支持单日与日期区间")
    @PostMapping("/api/statistics/daily-rebuild")
    @RequirePermission("statistics:rebuild")
    public R<Map<String, Object>> rebuild(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateTo) {
        LocalDateTime start = LocalDateTime.now();
        Map<String, Object> data = new HashMap<>();
        try {
            if (dateFrom != null || dateTo != null) {
                LocalDate from = dateFrom != null ? dateFrom : dateTo;
                LocalDate to = dateTo != null ? dateTo : dateFrom;
                List<RebuildResult> results = dailyStatService.rebuildRange(from, to);
                int rows = results.stream().mapToInt(RebuildResult::getAffectedRows).sum();
                data.put("results", results);
                data.put("affectedRows", rows);
                data.put("days", results.size());
                jobLogService.log("DAILY_STAT_MANUAL_RANGE", start, LocalDateTime.now(), true, rows,
                        "range=" + from + "~" + to);
            } else if (date == null) {
                /*
                 * 不带任何日期参数：按"最早一条打卡流水 ~ 今天"全量重算。
                 *
                 * 为什么不用 LocalDate.now() 只算今天：
                 * 首次部署后演示/历史流水都在过去若干天，att_daily_stat 是空的，
                 * 管理员点重算却只 Today 有数据，看板依旧全 0，
                 * 这在验收现场会被直接读成"统计模块没做"。
                 */
                LocalDate earliest = dailyStatService.earliestRecordDate();
                LocalDate from = earliest != null ? earliest : LocalDate.now();
                LocalDate to = LocalDate.now();
                List<RebuildResult> results = dailyStatService.rebuildRange(from, to);
                int rows = results.stream().mapToInt(RebuildResult::getAffectedRows).sum();
                data.put("results", results);
                data.put("affectedRows", rows);
                data.put("days", results.size());
                data.put("range", from + "~" + to);
                jobLogService.log("DAILY_STAT_MANUAL_ALL", start, LocalDateTime.now(), true, rows,
                        "range=" + from + "~" + to + "（按流水区间自动推导）");
            } else {
                LocalDate target = date;
                RebuildResult result = dailyStatService.rebuild(target);
                data.put("result", result);
                data.put("affectedRows", result.getAffectedRows());
                data.put("days", 1);
                jobLogService.log("DAILY_STAT_MANUAL", start, LocalDateTime.now(), true,
                        result.getAffectedRows(), "date=" + target);
            }
            data.put("statDays", dailyStatService.statDays());
            return R.ok(data);
        } catch (IllegalArgumentException e) {
            // 日期区间非法（倒序 / 超长）：属于调用方参数问题，返回 40001
            throw new BusinessException(ResultCode.PARAM_ERROR, e.getMessage());
        } catch (Exception e) {
            log.error("[STAT] 日汇总重算失败", e);
            jobLogService.log("DAILY_STAT_MANUAL", start, LocalDateTime.now(), false, 0, e.getMessage());
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "日汇总重算失败：" + e.getMessage());
        }
    }

    private String describeScope(AuthUser user) {
        // 与考勤记录查询共用同一套范围判定，避免两处口径漂移
        return dataScopeResolver.resolve(user);
    }
}
