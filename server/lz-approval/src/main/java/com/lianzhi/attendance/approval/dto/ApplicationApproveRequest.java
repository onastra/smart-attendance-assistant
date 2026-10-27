package com.lianzhi.attendance.approval.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 审批入参。
 */
@Data
public class ApplicationApproveRequest {

    /** APPROVE / REJECT */
    @NotBlank(message = "审批动作不能为空")
    private String action;

    /** 审批意见；驳回时必填（由服务层校验） */
    private String comment;
}
