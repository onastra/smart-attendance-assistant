package com.lianzhi.attendance.attendance.service;

import com.lianzhi.attendance.attendance.dto.DeptStatItem;
import com.lianzhi.attendance.attendance.dto.PlaceHourItem;
import com.lianzhi.attendance.attendance.dto.StatSummary;
import com.lianzhi.attendance.attendance.dto.TrendPoint;
import com.lianzhi.attendance.attendance.mapper.StatisticsMapper;
import com.lianzhi.attendance.attendance.query.StatisticsQuery;
import com.lianzhi.attendance.common.exception.BusinessException;
import com.lianzhi.attendance.common.result.ResultCode;
import com.lianzhi.attendance.framework.auth.AuthUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
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
    /** 未传日期范围时的默认窗口 */
    private static final int DEFAULT_RANGE_DAYS = 14;

    private static final String TYPE_DAY = "day";
    private static final String TYPE_WEEK = "week";
    private static final String TYPE_MONTH = "month";

    private final StatisticsMapper statisticsMapper;
    private final DataScopeResolver dataScopeResolver;

    public StatSummary summary(StatisticsQuery query, AuthUser user) {
        normalize(query, user);
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
        summary.setAttendanceRate(rate(presentDays, workdayPersonDays));

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

        String scope = dataScopeResolver.resolve(user);
        if (DataScopeResolver.SCOPE_DEPT.equals(scope)) {
            query.setDeptId(dataScopeResolver.deptIdOf(user));
        } else if (DataScopeResolver.SCOPE_SELF.equals(scope)) {
            query.setEmployeeId(dataScopeResolver.selfEmployeeId(user));
        }
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
