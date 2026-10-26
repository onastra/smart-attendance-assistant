package com.lianzhi.attendance.place.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 员工与场所的绑定视图。
 */
@Data
public class PlaceEmployee {

    private Long employeeId;
    private String empNo;
    private String employeeName;
    private String deptName;
    private LocalDateTime bindTime;
    /** 1 有效 0 已解绑 */
    private Integer status;
}
