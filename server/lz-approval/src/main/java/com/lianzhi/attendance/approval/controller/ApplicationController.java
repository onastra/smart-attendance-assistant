package com.lianzhi.attendance.approval.controller;

import com.lianzhi.attendance.approval.dto.ApplicationApproveRequest;
import com.lianzhi.attendance.approval.dto.ApplicationSubmitRequest;
import com.lianzhi.attendance.approval.entity.BizApplication;
import com.lianzhi.attendance.approval.query.ApplicationQuery;
import com.lianzhi.attendance.approval.service.ApplicationService;
import com.lianzhi.attendance.common.page.PageResult;
import com.lianzhi.attendance.common.result.R;
import com.lianzhi.attendance.framework.auth.AuthContext;
import com.lianzhi.attendance.framework.auth.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 请假/加班审批接口（模块 E）。
 */
@Tag(name = "请假/加班审批", description = "申请提交、我的申请、审批与状态机")
@Validated
@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    @Operation(summary = "提交申请", description = "员工提交请假/加班申请")
    @PostMapping
    @RequirePermission("application:submit")
    public R<BizApplication> submit(@Valid @RequestBody ApplicationSubmitRequest request) {
        Long employeeId = currentEmployeeId();
        return R.ok(applicationService.submit(request, employeeId));
    }

    @Operation(summary = "我的申请", description = "仅返回当前登录人本人的申请")
    @GetMapping("/mine")
    @RequirePermission("application:submit")
    public R<PageResult<BizApplication>> mine(ApplicationQuery query) {
        return R.ok(applicationService.pageMine(query, currentEmployeeId()));
    }

    @Operation(summary = "撤回申请", description = "申请人撤回本人待审批的申请")
    @PostMapping("/{id}/cancel")
    @RequirePermission("application:submit")
    public R<Void> cancel(@PathVariable Long id) {
        applicationService.cancel(id, currentEmployeeId());
        return R.ok();
    }

    @Operation(summary = "待审批队列", description = "返回待审批的申请，供审批人处理")
    @GetMapping("/todo")
    @RequirePermission("application:approve")
    public R<PageResult<BizApplication>> todo(ApplicationQuery query) {
        return R.ok(applicationService.pageTodo(query));
    }

    @Operation(summary = "全部申请", description = "管理员监督视图，返回全部状态的申请")
    @GetMapping
    @RequirePermission("application:list")
    public R<PageResult<BizApplication>> all(ApplicationQuery query) {
        return R.ok(applicationService.pageAll(query));
    }

    @Operation(summary = "审批", description = "通过或驳回；驳回必须填意见")
    @PostMapping("/{id}/approve")
    @RequirePermission("application:approve")
    public R<Void> approve(@PathVariable Long id,
                           @Valid @RequestBody ApplicationApproveRequest request) {
        applicationService.approve(id, request, AuthContext.currentUserId());
        return R.ok();
    }

    private Long currentEmployeeId() {
        var user = AuthContext.get();
        return user == null ? null : user.getEmployeeId();
    }
}
