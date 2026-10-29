package com.lianzhi.attendance.attendance.dto;

import lombok.Data;

/**
 * 出勤趋势预测点（需求书第 12 天课程："预测考勤、缺勤趋势"）。
 *
 * <p>actualRate 为历史真实出勤率；predictedRate 为预测值。
 * 历史点 predictedRate 为 null，未来点 actualRate 为 null，前端据此画实线/虚线。</p>
 */
@Data
public class ForecastPoint {

    /** 日期 yyyy-MM-dd */
    private String date;

    /** 真实出勤率（%），仅历史点有值 */
    private Double actualRate;

    /** 预测出勤率（%），仅未来点有值 */
    private Double predictedRate;
}
