package com.lianzhi.attendance.approval.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 请假/调休额度：每人每年一行（阶段 F：加班核算与调休闭环）。
 *
 * <p>口径（与 docs/sql/postgresql/patch-phaseF-leave-balance.sql 保持一致）：</p>
 * <ul>
 *   <li>已批"请假"（年假/事假/病假）：累加 leaveUsedHours，允许超额（额度用完后仍可提交，
 *       由审批人根据 leaveAvailable 为负自行把关——真实企业里超额请假转为事假扣薪，
 *       不在系统里硬卡）；</li>
 *   <li>已批"调休"假别：扣减调休余额（compensEarned - compensUsed），余额不足直接驳回
 *       审批请求并回滚，防止调休额度被透支；</li>
 *   <li>已批"加班"：durationHours 1:1 计入 compensEarnedHours（加班折调休）。</li>
 * </ul>
 */
@Data
public class LeaveBalance {

    private Long id;
    private Long employeeId;
    private int year;

    /** 年假额度（小时） */
    private BigDecimal leaveQuotaHours;
    /** 已用请假（年假/事假/病假，小时） */
    private BigDecimal leaveUsedHours;
    /** 加班折算调休额度（小时） */
    private BigDecimal compensEarnedHours;
    /** 已用调休（小时） */
    private BigDecimal compensUsedHours;

    private LocalDateTime updatedAt;

    // ===== 展示用辅助字段（SQL 别名填充，不入库）=====
    private String employeeName;
    /** 调休可用余额 = compens_earned - compens_used */
    private BigDecimal compensAvailable;
    /** 年假可用余额 = leave_quota - leave_used */
    private BigDecimal leaveAvailable;
}
