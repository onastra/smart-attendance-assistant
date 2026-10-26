package com.lianzhi.attendance.system.controller;

import com.lianzhi.attendance.common.result.R;
import com.lianzhi.attendance.framework.auth.RequirePermission;
import com.lianzhi.attendance.system.entity.Company;
import com.lianzhi.attendance.system.entity.Department;
import com.lianzhi.attendance.system.entity.Position;
import com.lianzhi.attendance.system.service.OrgService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 组织架构接口：公司、部门、岗位。
 */
@Tag(name = "组织架构", description = "公司、部门（树形）、岗位的管理")
@RestController
@RequestMapping("/api/org")
@RequiredArgsConstructor
public class OrgController {

    private final OrgService orgService;

    // ==================== 公司 ====================

    @Operation(summary = "公司列表")
    @GetMapping("/companies")
    @RequirePermission("system:company:list")
    public R<List<Company>> companies() {
        return R.ok(orgService.listCompanies());
    }

    @Operation(summary = "新建公司")
    @PostMapping("/companies")
    @RequirePermission("system:company:create")
    public R<Map<String, Object>> createCompany(@RequestBody Company company) {
        return R.ok(Map.of("id", orgService.createCompany(company)));
    }

    @Operation(summary = "编辑公司")
    @PutMapping("/companies/{id}")
    @RequirePermission("system:company:update")
    public R<Void> updateCompany(@PathVariable Long id, @RequestBody Company company) {
        company.setId(id);
        orgService.updateCompany(company);
        return R.ok();
    }

    @Operation(summary = "删除公司", description = "存在子公司时拒绝；否则转为停用状态")
    @DeleteMapping("/companies/{id}")
    @RequirePermission("system:company:delete")
    public R<Void> deleteCompany(@PathVariable Long id) {
        orgService.deleteCompany(id);
        return R.ok();
    }

    // ==================== 部门 ====================

    @Operation(summary = "部门列表", description = "companyId 为空时返回全部部门")
    @GetMapping("/departments")
    @RequirePermission("system:dept:list")
    public R<List<Department>> departments(@RequestParam(required = false) Long companyId) {
        return R.ok(orgService.listDepartments(companyId));
    }

    @Operation(summary = "部门树", description = "按 parentId 组装的层级结构")
    @GetMapping("/departments/tree")
    @RequirePermission("system:dept:list")
    public R<List<Department>> departmentTree(@RequestParam(required = false) Long companyId) {
        return R.ok(orgService.departmentTree(companyId));
    }

    @Operation(summary = "新建部门")
    @PostMapping("/departments")
    @RequirePermission("system:dept:create")
    public R<Map<String, Object>> createDepartment(@RequestBody Department dept) {
        return R.ok(Map.of("id", orgService.createDepartment(dept)));
    }

    @Operation(summary = "编辑部门")
    @PutMapping("/departments/{id}")
    @RequirePermission("system:dept:update")
    public R<Void> updateDepartment(@PathVariable Long id, @RequestBody Department dept) {
        dept.setId(id);
        orgService.updateDepartment(dept);
        return R.ok();
    }

    @Operation(summary = "删除部门", description = "存在子部门或仍在职员工时拒绝")
    @DeleteMapping("/departments/{id}")
    @RequirePermission("system:dept:delete")
    public R<Void> deleteDepartment(@PathVariable Long id) {
        orgService.deleteDepartment(id);
        return R.ok();
    }

    // ==================== 岗位 ====================

    @Operation(summary = "岗位列表")
    @GetMapping("/positions")
    @RequirePermission("system:position:list")
    public R<List<Position>> positions(@RequestParam(required = false) Long companyId) {
        return R.ok(orgService.listPositions(companyId));
    }

    @Operation(summary = "新建岗位")
    @PostMapping("/positions")
    @RequirePermission("system:position:create")
    public R<Map<String, Object>> createPosition(@RequestBody Position position) {
        return R.ok(Map.of("id", orgService.createPosition(position)));
    }

    @Operation(summary = "编辑岗位")
    @PutMapping("/positions/{id}")
    @RequirePermission("system:position:update")
    public R<Void> updatePosition(@PathVariable Long id, @RequestBody Position position) {
        position.setId(id);
        orgService.updatePosition(position);
        return R.ok();
    }

    @Operation(summary = "删除岗位", description = "仍有关联员工时拒绝")
    @DeleteMapping("/positions/{id}")
    @RequirePermission("system:position:delete")
    public R<Void> deletePosition(@PathVariable Long id) {
        orgService.deletePosition(id);
        return R.ok();
    }
}
