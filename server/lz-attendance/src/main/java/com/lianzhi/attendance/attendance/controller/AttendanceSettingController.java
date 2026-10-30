package com.lianzhi.attendance.attendance.controller;

import com.lianzhi.attendance.attendance.entity.AttWorkConfig;
import com.lianzhi.attendance.attendance.service.WorkConfigService;
import com.lianzhi.attendance.common.result.R;
import com.lianzhi.attendance.framework.auth.AuthContext;
import com.lianzhi.attendance.framework.auth.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 全局考勤时间设置（管理员侧）。
 *
 * <p>权限沿用 attendance:record:admin（考勤管理口径）：能查全员考勤记录的
 * 管理员/主管才允许调整判定口径，避免为此新增需要种子数据同步的权限码。</p>
 */
@Tag(name = "考勤设置", description = "全局上下班时间与迟到/早退宽限分钟数，修改后打卡判定立即生效")
@RestController
@RequestMapping("/api/attendance/settings")
@RequiredArgsConstructor
public class AttendanceSettingController {

    private final WorkConfigService workConfigService;

    @Operation(summary = "查询当前全局考勤时间配置")
    @GetMapping
    @RequirePermission("attendance:record:admin")
    public R<AttWorkConfig> get() {
        return R.ok(workConfigService.get());
    }

    @Operation(summary = "更新全局考勤时间配置",
            description = "上班时间/下班时间/迟到宽限/早退宽限；下班必须晚于上班，宽限 0~240 分钟")
    @PutMapping
    @RequirePermission("attendance:record:admin")
    public R<AttWorkConfig> update(@RequestBody AttWorkConfig request) {
        return R.ok(workConfigService.update(request, AuthContext.currentUserId()));
    }
}
