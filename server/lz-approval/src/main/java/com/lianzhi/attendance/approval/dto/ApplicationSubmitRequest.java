package com.lianzhi.attendance.approval.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 提交申请入参。
 */
@Data
public class ApplicationSubmitRequest {

    /** LEAVE / OVERTIME */
    @NotBlank(message = "申请类型不能为空")
    private String type;

    /** 子类型：事假/病假/年假 等（可选） */
    private String subType;

    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;

    @NotBlank(message = "请假/加班事由不能为空")
    private String reason;

    /** 附件 sys_file.id（可选） */
    private Long attachmentFileId;
}
