package com.lianzhi.attendance.system.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 岗位。挂在公司下，员工可引用。
 */
@Data
public class Position {

    private Long id;
    private Long companyId;
    private String positionName;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String companyName;
}
