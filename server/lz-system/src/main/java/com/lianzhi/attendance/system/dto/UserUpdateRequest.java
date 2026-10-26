package com.lianzhi.attendance.system.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 编辑账号入参。
 */
@Getter
@Setter
public class UserUpdateRequest {

    private String nickname;
    private String username;
    private Long employeeId;
    private Integer status;
    /** 传入即为全量覆盖：空数组表示清空该账号的角色 */
    private List<Long> roleIds;
}
