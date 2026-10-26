package com.lianzhi.attendance.system.query;

import com.lianzhi.attendance.common.page.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 角色分页查询条件。
 */
@Getter
@Setter
public class RoleQuery extends PageQuery {

    private String keyword;
    private Integer status;
}
