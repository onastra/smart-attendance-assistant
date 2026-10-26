package com.lianzhi.attendance.attendance.dto;

import lombok.Data;

/**
 * 日汇总重算结果。
 *
 * <p>把"影响了几行、其中多少是演示数据"直接回给调用方：
 * 重算是个写操作，调用方（看板上的"重新计算"按钮）需要看到执行结果，
 * 而不是点了没反应。</p>
 */
@Data
public class RebuildResult {

    /** 重算的日期 */
    private String date;

    /** 覆盖在职员工数（每行一个人，含没来的人） */
    private int employeeCount;

    /** 写入/更新的汇总行数 */
    private int affectedRows;

    /** 其中标记为演示数据的行数 */
    private int sampleRows;

    /** 判定为缺勤的行数，便于一眼看出"这天有多少人没来" */
    private int absentRows;

    /** 开始与结束时间（毫秒差即耗时），用于任务日志与前端提示 */
    private long costMillis;

    /** 幂等标识：true 表示本次重算的数据与上次完全一致 */
    private boolean idempotent = true;
}
