package com.lianzhi.attendance.attendance.controller;

import com.lianzhi.attendance.attendance.dto.CheckinRequest;
import com.lianzhi.attendance.attendance.dto.CheckinResult;
import com.lianzhi.attendance.attendance.service.CheckinService;
import com.lianzhi.attendance.attendance.util.WebUtil;
import com.lianzhi.attendance.common.result.R;
import com.lianzhi.attendance.framework.auth.AuthContext;
import com.lianzhi.attendance.framework.auth.AuthUser;
import com.lianzhi.attendance.framework.auth.RequireLogin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * H5 打卡接口。
 *
 * <p>身份一律取自 token：请求体里不接受 employeeId，
 * 否则改一个字段就能替别人打卡。</p>
 */
@Tag(name = "打卡", description = "H5 扫码/手动输入打卡，含幂等请求号")
@Validated
@RestController
@RequestMapping("/api/checkin")
@RequiredArgsConstructor
public class CheckinController {

    private final CheckinService checkinService;

    @Operation(summary = "获取本次打卡的幂等请求号",
            description = "进入打卡页时调用一次，提交时带回；重复提交不会产生第二条记录")
    @PostMapping("/request-id")
    @RequireLogin
    public R<Map<String, Object>> requestId() {
        AuthUser user = AuthContext.get();
        String requestId = checkinService.issueRequestId(user == null ? null : user.getUserId());
        return R.ok(Map.of("requestId", requestId));
    }

    @Operation(summary = "提交打卡",
            description = "校验顺序：二维码格式 → 签名 → 状态/有效期 → 场所启用 → 员工在职 → 场所绑定 → 幂等 → 重复窗口 → 围栏")
    @PostMapping
    @RequireLogin
    public R<CheckinResult> checkin(@Valid @RequestBody CheckinRequest request,
                                    HttpServletRequest httpRequest) {
        AuthUser user = AuthContext.get();
        return R.ok(checkinService.checkin(request, user.getUserId(), user.getEmployeeId(),
                WebUtil.clientIp(httpRequest)));
    }
}
