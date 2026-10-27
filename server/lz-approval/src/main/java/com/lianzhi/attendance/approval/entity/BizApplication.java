package com.lianzhi.attendance.approval.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 请假/加班申请单。
 *
 * <p>状态机：SUBMITTED → APPROVED / REJECTED / CANCELLED（终态），终态不可再审批。
 * 单级审批：提交后 current_approver_id 为空，任意持 application:approve 权限的人都可处理。</p>
 */
@Data
public class BizApplication {

    /** 类型：请假 */
    public static final String TYPE_LEAVE = "LEAVE";
    /** 类型：加班 */
    public static final String TYPE_OVERTIME = "OVERTIME";

    /** 草稿（预留，当前提交即生效） */
    public static final String STATUS_DRAFT = "DRAFT";
    /** 已提交待审批 */
    public static final String STATUS_SUBMITTED = "SUBMITTED";
    /** 已通过 */
    public static final String STATUS_APPROVED = "APPROVED";
    /** 已驳回 */
    public static final String STATUS_REJECTED = "REJECTED";
    /** 已撤回（申请人主动取消） */
    public static final String STATUS_CANCELLED = "CANCELLED";

    private Long id;
    private String applicationNo;
    private Long employeeId;
    private String type;
    private String subType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal durationHours;
    private String reason;
    private Long attachmentFileId;
    private String status;
    private Long currentApproverId;
    private LocalDateTime submittedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;

    // ===== 展示用辅助字段（由 SQL 别名填充，不入库）=====
    /** 申请人姓名（JOIN hr_employee） */
    private String employeeName;
    /** 最近一次审批人姓名（JOIN sys_user） */
    private String approverName;
    /** 最近一次审批动作 APPROVE/REJECT */
    private String lastAction;
    /** 最近一次审批意见 */
    private String lastComment;
}
