package com.lianzhi.attendance.system.controller;

import com.lianzhi.attendance.common.page.PageResult;
import com.lianzhi.attendance.common.result.R;
import com.lianzhi.attendance.framework.auth.RequirePermission;
import com.lianzhi.attendance.system.entity.Employee;
import com.lianzhi.attendance.system.query.EmployeeQuery;
import com.lianzhi.attendance.system.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 员工档案接口。
 */
@Tag(name = "员工管理", description = "员工档案的增删改查与在职状态变更")
@Validated
@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @Operation(summary = "员工分页列表", description = "支持按姓名模糊、工号精确、部门、公司、在职状态筛选")
    @GetMapping
    @RequirePermission("hr:employee:list")
    public R<PageResult<Employee>> page(EmployeeQuery query) {
        return R.ok(employeeService.page(query));
    }

    @Operation(summary = "员工详情")
    @GetMapping("/{id}")
    @RequirePermission("hr:employee:list")
    public R<Employee> detail(@PathVariable Long id) {
        return R.ok(employeeService.detail(id));
    }

    @Operation(summary = "新建员工", description = "empNo 留空时由系统自动生成")
    @PostMapping
    @RequirePermission("hr:employee:create")
    public R<Map<String, Object>> create(@Valid @RequestBody Employee employee) {
        return R.ok(Map.of("id", employeeService.create(employee)));
    }

    @Operation(summary = "编辑员工")
    @PutMapping("/{id}")
    @RequirePermission("hr:employee:update")
    public R<Void> update(@PathVariable Long id, @RequestBody Employee employee) {
        employee.setId(id);
        employeeService.update(employee);
        return R.ok();
    }

    @Operation(summary = "离职（物理删除）", description = "删除员工档案及其关联数据（打卡流水/日统计/申请单/人脸特征/场所绑定）；不复留离职记录，误删需重新建档")
    @DeleteMapping("/{id}")
    @RequirePermission("hr:employee:update")
    public R<Void> delete(@PathVariable Long id) {
        employeeService.delete(id);
        return R.ok();
    }
}
