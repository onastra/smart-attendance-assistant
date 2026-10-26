package com.lianzhi.attendance.system.query;

import com.lianzhi.attendance.common.page.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 员工分页查询条件。
 */
@Getter
@Setter
public class EmployeeQuery extends PageQuery {

    /** 姓名模糊匹配 */
    private String name;
    /** 工号精确匹配 */
    private String empNo;
    private Long deptId;
    private Long companyId;
    /** 1 在职 0 停用；null 表示不过滤 */
    private Integer status;
}
