package com.lianzhi.attendance.approval.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批流水（每条申请可能有多条审批记录，当前为单级）。
 */
@Data
public class BizApplicationApproval {

    /** 审批动作：通过 */
    public static final String ACTION_APPROVE = "APPROVE";
    /** 审批动作：驳回 */
    public static final String ACTION_REJECT = "REJECT";

    private Long id;
    private Long applicationId;
    private Long approverId;
    private String action;
    private String comment;
    private LocalDateTime createdAt;
}
