package com.lianzhi.attendance.attendance.service;

import com.lianzhi.attendance.attendance.dto.AbsentEmployee;
import com.lianzhi.attendance.attendance.dto.DeptStatItem;
import com.lianzhi.attendance.attendance.dto.ForecastPoint;
import com.lianzhi.attendance.attendance.dto.PlaceHourItem;
import com.lianzhi.attendance.attendance.dto.StatSummary;
import com.lianzhi.attendance.attendance.dto.TrendPoint;
import com.lianzhi.attendance.attendance.dto.WorkHourPoint;
import com.lianzhi.attendance.attendance.mapper.StatisticsMapper;
import com.lianzhi.attendance.attendance.query.StatisticsQuery;
import com.lianzhi.attendance.common.exception.BusinessException;
import com.lianzhi.attendance.common.result.ResultCode;
import com.lianzhi.attendance.framework.auth.AuthUser;
import com.lianzhi.attendance.framework.cache.CacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 考勤统计。
 *
 * <p>两条硬口径：
 * <ol>
 *   <li>数据范围与考勤记录查询共用 {@link DataScopeResolver}，ADMIN 看全量、
 *       部门主管看本部门、其他人只看自己——统计接口不能比记录接口看到更多。</li>
 *   <li>响应必须带 <code>dataSource</code>：范围内只要掺了演示补录数据就如实标注
 *       （需求 IDX-D8），绝不能把 SAMPLE 流水算出来的数字当成真实考勤呈现。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 未传日期范围时的默认窗口 */
    private static final int DEFAULT_RANGE_DAYS = 14;

    private static final String TYPE_DAY = "day";
    private static final String TYPE_WEEK = "week";
    private static final String TYPE_MONTH = "month";

    private final StatisticsMapper statisticsMapper;
    private final DataScopeResolver dataScopeResolver;
    private final CacheService cacheService;

    /** 概览缓存秒数：当天数据会随打卡变化，因此刻意取短 TTL */
    @Value("${lz.cache.ttl.statistics-summary-seconds:60}")
    private long summaryTtlSeconds;

    public StatSummary summary(StatisticsQuery query, AuthUser user) {
        normalize(query, user);

        // 缓存键必须包含数据范围（部门/员工/日期）：
        // 否则部门主管可能拿到管理员视角缓存下来的全量数字，等同于越权。
        String cacheKey = "statistics:summary:" + query.getDateFrom() + ":" + query.getDateTo()
                + ":dept=" + query.getDeptId() + ":emp=" + query.getEmployeeId();

        StatSummary hit = cacheService.get(cacheKey, StatSummary.class);
        if (hit != null) {
            // 命中：如实标记 cached=true，generatedAt 保留首次生成时间
            hit.setCached(true);
            return hit;
        }

        StatSummary summary = loadSummary(query);
        summary.setCached(false);
        summary.setGeneratedAt(LocalDateTime.now().format(TIMESTAMP_FORMATTER));
        cacheService.put(cacheKey, summary, Duration.ofSeconds(summaryTtlSeconds));
        return summary;
    }

    /** 真实计算一次概览（缓存未命中或缓存关闭时走这里）。 */
    private StatSummary loadSummary(StatisticsQuery query) {
        StatSummary summary = statisticsMapper.selectSummary(query);
        if (summary == null) {
            // 范围内没有汇总数据：返回全零而不是 null，前端空态才好处理
            summary = new StatSummary();
        }
        summary.setDateFrom(query.getDateFrom());
        summary.setDateTo(query.getDateTo());

        long workdayPersonDays = summary.getPersonDays() - summary.getRestDays();
        summary.setWorkdayPersonDays(workdayPersonDays);
        long presentDays = summary.getNormalDays() + summary.getLateDays() + summary.getEarlyDays();
        // 出勤率分母排除请假人天（E-07）：请假是合规缺勤，不该拉低出勤率
        long denominator = workdayPersonDays - summary.getLeaveDays();
        summary.setAttendanceRate(rate(presentDays, denominator));

        applyDataSource(summary);
        return summary;
    }

    public List<TrendPoint> trend(StatisticsQuery query, AuthUser user) {
        normalize(query, user);
        String type = query.getType() == null ? TYPE_DAY : query.getType().toLowerCase();
        if (!TYPE_DAY.equals(type) && !TYPE_WEEK.equals(type) && !TYPE_MONTH.equals(type)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "趋势粒度只支持 day / week / month");
        }
        query.setType(type);
        // 分组表达式由 service 层的白名单常量决定，SQL 里用静态 choose 分支，不存在注入面
        return statisticsMapper.selectTrend(query, type);
    }

    public List<DeptStatItem> department(StatisticsQuery query, AuthUser user) {
        normalize(query, user);
        return statisticsMapper.selectDeptStat(query);
    }

    /**
     * 场所时段分布。
     *
     * <p>返回两个列表：按小时拆分的明细，以及场所维度总量（供前端筛选与图例）。
     * 两个都为空时前端展示空态，不报错。</p>
     */
    public Map<String, Object> placeHeat(StatisticsQuery query, AuthUser user) {
        normalize(query, user);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dateFrom", query.getDateFrom());
        data.put("dateTo", query.getDateTo());
        data.put("hours", statisticsMapper.selectPlaceHour(query));
        data.put("places", statisticsMapper.selectPlaceTotal(query));
        data.put("mapEnabled", false);
        data.put("note", "未配置地图服务 Key，返回时段分布数据而非地图热力图层");
        return data;
    }

    /**
     * 补齐查询条件：日期缺省、数据范围收敛。
     *
     * <p>范围收敛在服务端做，前端传什么都不影响结果——
     * 部门主管把 deptId 改成别的部门也看不到那个部门的数据。</p>
     */
    private void normalize(StatisticsQuery query, AuthUser user) {
        if (query.getDateFrom() == null || query.getDateFrom().isBlank()
                || query.getDateTo() == null || query.getDateTo().isBlank()) {
            LocalDate to = LocalDate.now();
            query.setDateFrom(to.minusDays(DEFAULT_RANGE_DAYS - 1).format(DATE_FORMATTER));
            query.setDateTo(to.format(DATE_FORMATTER));
        }
        if (query.getDateFrom().compareTo(query.getDateTo()) > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "开始日期不能晚于结束日期");
        }

        applyScope(query, user);
    }

    /** 只收敛数据范围，不动日期（员工画像等自带日期窗口的接口用） */
    private void applyScope(StatisticsQuery query, AuthUser user) {
        String scope = dataScopeResolver.resolve(user);
        if (DataScopeResolver.SCOPE_DEPT.equals(scope)) {
            query.setDeptId(dataScopeResolver.deptIdOf(user));
        } else if (DataScopeResolver.SCOPE_SELF.equals(scope)) {
            query.setEmployeeId(dataScopeResolver.selfEmployeeId(user));
        }
    }

    /**
     * 出勤趋势预测（需求书"数据预测"）。
     *
     * <p>算法刻意取"同星期均值"而不是线性回归：出勤率以周为周期波动，
     * 周末天然是 0，直接回归会把工作日的预测拖向周末，数字没法看。
     * 取近 28 天里同一个星期几的出勤率平均，外推未来 7 天，方法简单且可解释。</p>
     */
    public Map<String, Object> forecast(AuthUser user) {
        LocalDate today = LocalDate.now();
        StatisticsQuery query = new StatisticsQuery();
        query.setType(TYPE_DAY);
        query.setDateFrom(today.minusDays(27).format(DATE_FORMATTER));
        query.setDateTo(today.format(DATE_FORMATTER));
        applyScope(query, user);

        List<TrendPoint> history = statisticsMapper.selectTrend(query, TYPE_DAY);

        // 同星期均值：key=DayOfWeek，value=近4周该星期几的平均出勤率
        Map<java.time.DayOfWeek, double[]> weekdaySum = new LinkedHashMap<>();
        for (TrendPoint p : history) {
            LocalDate d = LocalDate.parse(p.getPeriod(), DATE_FORMATTER);
            double[] acc = weekdaySum.computeIfAbsent(d.getDayOfWeek(), k -> new double[2]);
            acc[0] += p.getAttendanceRate();
            acc[1] += 1;
        }
        double overallAvg = history.stream().mapToDouble(TrendPoint::getAttendanceRate).average().orElse(0);

        List<ForecastPoint> points = new java.util.ArrayList<>();
        for (TrendPoint p : history) {
            ForecastPoint fp = new ForecastPoint();
            fp.setDate(p.getPeriod());
            fp.setActualRate(p.getAttendanceRate());
            points.add(fp);
        }
        for (int i = 1; i <= 7; i++) {
            LocalDate future = today.plusDays(i);
            double[] acc = weekdaySum.get(future.getDayOfWeek());
            ForecastPoint fp = new ForecastPoint();
            fp.setDate(future.format(DATE_FORMATTER));
            // 某星期几一次都没出现过（如新系统刚上线不满一周）时退回整体均值
            fp.setPredictedRate(acc != null && acc[1] > 0
                    ? Math.round(acc[0] / acc[1] * 10.0) / 10.0
                    : Math.round(overallAvg * 10.0) / 10.0);
            points.add(fp);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("points", points);
        data.put("method", "同星期均值（近 28 天）外推未来 7 天");
        data.put("historyDays", history.size());
        return data;
    }

    /**
     * 今日未打卡 / 请假名单（需求书"离岗员工数据分布"）。
     *
     * <p>已打卡的员工不返回；返回的名单分两组：ABSENT（今日未打卡）与 LEAVE（已批请假）。</p>
     */
    public Map<String, Object> todayAbsent(AuthUser user) {
        LocalDate today = LocalDate.now();
        StatisticsQuery scopeQuery = new StatisticsQuery();
        applyScope(scopeQuery, user);

        List<AbsentEmployee> all = statisticsMapper.selectTodayAbsent(
                today.atStartOfDay(), today.plusDays(1).atStartOfDay(),
                scopeQuery.getDeptId(), scopeQuery.getEmployeeId());

        List<AbsentEmployee> absent = new java.util.ArrayList<>();
        List<AbsentEmployee> leave = new java.util.ArrayList<>();
        for (AbsentEmployee item : all) {
            if (AbsentEmployee.STATUS_LEAVE.equals(item.getStatus())) {
                leave.add(item);
            } else if (AbsentEmployee.STATUS_ABSENT.equals(item.getStatus())) {
                absent.add(item);
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("date", today.format(DATE_FORMATTER));
        data.put("absent", absent);
        data.put("leave", leave);
        data.put("absentCount", absent.size());
        data.put("leaveCount", leave.size());
        return data;
    }

    /** 人均工时趋势（需求书"员工工时分析"） */
    public List<WorkHourPoint> workHours(StatisticsQuery query, AuthUser user) {
        normalize(query, user);
        return statisticsMapper.selectWorkHours(query);
    }

    /**
     * 员工画像（需求书"局部画像"）：近 30 天出勤构成 + 每日出勤率走势。
     *
     * <p>越权控制：SELF 范围强制看自己；DEPT 范围只允许看本部门员工；
     * 目标员工不存在或不在范围内时直接 403，不泄漏存在性。</p>
     */
    public Map<String, Object> employeeProfile(StatisticsQuery query, AuthUser user) {
        String scope = dataScopeResolver.resolve(user);
        Long target = query.getEmployeeId();

        if (DataScopeResolver.SCOPE_SELF.equals(scope)) {
            target = dataScopeResolver.selfEmployeeId(user);
        } else if (DataScopeResolver.SCOPE_DEPT.equals(scope) && target != null) {
            Long targetDept = statisticsMapper.selectEmployeeDeptId(target);
            if (targetDept == null || !targetDept.equals(dataScopeResolver.deptIdOf(user))) {
                throw new BusinessException(ResultCode.FORBIDDEN, "只能查看本部门员工的画像");
            }
        }
        if (target == null) {
            target = dataScopeResolver.selfEmployeeId(user);
        }
        if (target == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "当前账号未绑定员工档案，无法生成画像");
        }

        LocalDate today = LocalDate.now();
        StatisticsQuery window = new StatisticsQuery();
        window.setEmployeeId(target);
        window.setDateFrom(today.minusDays(29).format(DATE_FORMATTER));
        window.setDateTo(today.format(DATE_FORMATTER));

        StatSummary summary = loadSummary(window);
        List<TrendPoint> trend = statisticsMapper.selectTrend(window, TYPE_DAY);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("employeeId", target);
        data.put("summary", summary);
        data.put("trend", trend);
        data.put("window", window.getDateFrom() + "~" + window.getDateTo());
        return data;
    }

    private void applyDataSource(StatSummary summary) {
        long personDays = summary.getPersonDays();
        long sampleDays = summary.getSampleDays();
        if (personDays == 0 || sampleDays == 0) {
            summary.setDataSource("real");
            summary.setDataSourceNote("全部来自真实打卡流水");
            return;
        }
        if (sampleDays >= personDays) {
            summary.setDataSource("sample");
            summary.setDataSourceNote("全部为演示补录数据（14 天趋势演示），不代表真实考勤");
            return;
        }
        summary.setDataSource("mixed");
        summary.setDataSourceNote("含 " + sampleDays + " / " + personDays + " 人天的演示补录数据，真实打卡与演示数据混合统计");
    }

    private double rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return Math.round(numerator * 1000.0 / denominator) / 10.0;
    }
}
