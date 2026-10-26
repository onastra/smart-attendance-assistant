package com.lianzhi.attendance.system.query;

import com.lianzhi.attendance.common.page.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 账号分页查询条件。
 */
@Getter
@Setter
public class UserQuery extends PageQuery {

    /** 用户名或昵称模糊匹配 */
    private String keyword;
    private Integer status;
    private Long roleId;
}
