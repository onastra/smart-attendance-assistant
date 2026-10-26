package com.lianzhi.attendance.attendance.query;

import lombok.Data;

/**
 * 统计查询条件。
 *
 * <p>dateFrom / dateTo 是必填项：统计接口如果允许不带日期范围，
 * 默认就会变成全表扫描，看板每打开一次都是一次全量聚合。
 * 缺省由控制器兜底为"最近 14 天"。</p>
 */
@Data
public class StatisticsQuery {

    /** 起始日期（含），yyyy-MM-dd */
    private String dateFrom;

    /** 截止日期（含），yyyy-MM-dd */
    private String dateTo;

    /** 部门过滤，由数据范围收敛写入 */
    private Long deptId;

    /** 员工过滤，SELF 范围时强制写入 */
    private Long employeeId;

    /** 场所过滤（场所时段分布用） */
    private Long placeId;

    /** 趋势粒度：day / week / month */
    private String type = "day";
}
