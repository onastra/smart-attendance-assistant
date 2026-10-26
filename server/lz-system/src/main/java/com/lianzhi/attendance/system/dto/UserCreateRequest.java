package com.lianzhi.attendance.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 新建账号入参。
 */
@Getter
@Setter
public class UserCreateRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名长度不能超过 64")
    private String username;

    @NotBlank(message = "初始密码不能为空")
    @Size(min = 6, max = 64, message = "初始密码长度需在 6~64 之间")
    private String password;

    private String nickname;
    private Long employeeId;
    private Integer status;
    private List<Long> roleIds;
}
