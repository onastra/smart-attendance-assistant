package com.lianzhi.attendance.system.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工档案。
 *
 * <p><code>emp_no</code> 由业务生成并保证唯一，是员工的业务主键；<code>id</code> 仅作内部关联。</p>
 */
@Data
public class Employee {

    private Long id;

    /** 允许留空：留空时由服务端生成，但一旦生成不可重复 */
    @Size(max = 32, message = "工号长度不能超过 32")
    private String empNo;

    @NotBlank(message = "员工姓名不能为空")
    @Size(max = 64, message = "员工姓名长度不能超过 64")
    private String name;
    /** 0 未知 1 男 2 女 */
    private Integer gender;
    /** 允许为空：手机号非必填；填写时按中国大陆号码格式校验 */
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
    private String email;
    private Long companyId;
    private Long deptId;
    private Long positionId;
    private java.time.LocalDate hireDate;
    /** 1 在职 0 离职/停用 */
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ===== 联表查询的冗余展示字段（非数据库列）=====
    private String companyName;
    private String deptName;
    private String positionName;
}
