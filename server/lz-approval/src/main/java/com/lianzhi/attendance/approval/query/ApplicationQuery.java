package com.lianzhi.attendance.approval.query;

import com.lianzhi.attendance.common.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 申请单列表查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ApplicationQuery extends PageQuery {

    /** 类型 LEAVE / OVERTIME */
    private String type;

    /** 状态 SUBMITTED / APPROVED / REJECTED / CANCELLED */
    private String status;

    /** 申请人（我的申请场景由服务层填充，避免前端越权篡改） */
    private Long employeeId;
}
