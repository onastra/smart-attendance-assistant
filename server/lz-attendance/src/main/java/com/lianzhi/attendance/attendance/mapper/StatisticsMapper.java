package com.lianzhi.attendance.attendance.mapper;

import com.lianzhi.attendance.attendance.dto.DeptStatItem;
import com.lianzhi.attendance.attendance.dto.PlaceHourItem;
import com.lianzhi.attendance.attendance.dto.StatSummary;
import com.lianzhi.attendance.attendance.dto.TrendPoint;
import com.lianzhi.attendance.attendance.query.StatisticsQuery;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 统计查询：全部走 SQL 聚合，不做内存计算。
 *
 * <p>聚合口径统一落在 att_daily_stat（日汇总）上，只有"场所时段分布"需要小时粒度，
 * 才回落到 att_record。这样看板每次打开只扫一张每人每天一行的汇总表。</p>
 */
public interface StatisticsMapper {

    StatSummary selectSummary(StatisticsQuery query);

    /**
     * 趋势数据。
     *
     * @param groupExpr 分组表达式，由调用方按 day/week/month 传入，
     *                  避免把三种粒度写成三段几乎一样的 SQL。
     */
    List<TrendPoint> selectTrend(@Param("query") StatisticsQuery query, @Param("groupExpr") String groupExpr);

    List<DeptStatItem> selectDeptStat(StatisticsQuery query);

    List<PlaceHourItem> selectPlaceHour(StatisticsQuery query);

    /** 场所列表（带统计范围内的打卡次数），用于前端筛选下拉与图例 */
    List<PlaceHourItem> selectPlaceTotal(StatisticsQuery query);
}
