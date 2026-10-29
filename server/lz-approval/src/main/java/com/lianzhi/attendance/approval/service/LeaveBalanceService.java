package com.lianzhi.attendance.approval.service;

import com.lianzhi.attendance.approval.entity.BizApplication;
import com.lianzhi.attendance.approval.entity.LeaveBalance;
import com.lianzhi.attendance.approval.mapper.LeaveBalanceMapper;
import com.lianzhi.attendance.common.exception.BusinessException;
import com.lianzhi.attendance.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 请假/调休额度服务（阶段 F：加班核算与调休闭环）。
 *
 * <p>额度联动只发生在<b>审批通过</b>那一刻（见
 * {@link ApplicationService#approve}）：</p>
 * <ul>
 *   <li>LEAVE + 年假/事假/病假 → 累加已用请假小时（允许超额）；</li>
 *   <li>LEAVE + 调休 → 校验调休余额后扣减，余额不足抛业务异常，
 *       整个审批事务一起回滚（申请单不会被置为已通过）；</li>
 *   <li>OVERTIME → 加班时长 1:1 折算进调休额度。</li>
 * </ul>
 *
 * <p>额度行懒初始化：第一次用到时按默认年假额度建行，不要求先跑管理端初始化。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveBalanceService {

    /** 调休假别（与前端 Submit 下拉一致；提交单里的 sub_type 为自由文本，此处精确匹配） */
    public static final String SUB_TYPE_COMPENS = "调休";

    private final LeaveBalanceMapper balanceMapper;

    /** 默认年假额度（小时）：patch-phaseG 给在职员工预置 40h，懒初始化沿用同一默认 */
    @Value("${lz.attendance.default-annual-quota-hours:40}")
    private BigDecimal defaultAnnualQuotaHours;

    /**
     * 审批通过后的额度联动。
     *
     * <p>以 REQUIRED 传播参与调用方事务：余额不足抛异常时，审批状态更新一并回滚。</p>
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void applyApproved(BizApplication app) {
        if (app == null || app.getEmployeeId() == null || app.getDurationHours() == null) {
            return;
        }
        int year = app.getStartTime() != null ? app.getStartTime().getYear() : LocalDate.now().getYear();
        ensureRow(app.getEmployeeId(), year);

        if (BizApplication.TYPE_OVERTIME.equals(app.getType())) {
            balanceMapper.addCompensEarned(app.getEmployeeId(), year, app.getDurationHours());
            log.info("[BALANCE] 加班折调休 employeeId={} year={} +{}h", app.getEmployeeId(), year, app.getDurationHours());
            return;
        }

        // LEAVE：调休假别扣调休余额；其余假别累加已用请假
        if (SUB_TYPE_COMPENS.equals(app.getSubType())) {
            LeaveBalance balance = balanceMapper.selectOne(app.getEmployeeId(), year);
            BigDecimal available = balance.getCompensEarnedHours().subtract(balance.getCompensUsedHours());
            if (available.compareTo(app.getDurationHours()) < 0) {
                throw new BusinessException(ResultCode.CONFLICT,
                        "调休额度不足：可用 " + available.stripTrailingZeros().toPlainString()
                                + "h，本次申请 " + app.getDurationHours() + "h");
            }
            balanceMapper.addCompensUsed(app.getEmployeeId(), year, app.getDurationHours());
            log.info("[BALANCE] 调休扣减 employeeId={} year={} -{}h", app.getEmployeeId(), year, app.getDurationHours());
        } else {
            balanceMapper.addLeaveUsed(app.getEmployeeId(), year, app.getDurationHours());
            log.info("[BALANCE] 请假累加 employeeId={} year={} +{}h subType={}",
                    app.getEmployeeId(), year, app.getDurationHours(), app.getSubType());
        }
    }

    /** 本人当年额度（不存在则懒初始化后返回） */
    public LeaveBalance myBalance(Long employeeId) {
        return balanceOf(employeeId, LocalDate.now().getYear());
    }

    /** 指定员工某年额度（供管理端与员工画像使用） */
    public LeaveBalance balanceOf(Long employeeId, int year) {
        ensureRow(employeeId, year);
        LeaveBalance balance = balanceMapper.selectWithBalance(employeeId, year);
        if (balance == null) {
            // 员工不存在（已物理删除等）：返回全零行而不是 500
            balance = new LeaveBalance();
            balance.setEmployeeId(employeeId);
            balance.setYear(year);
            balance.setLeaveQuotaHours(BigDecimal.ZERO);
            balance.setLeaveUsedHours(BigDecimal.ZERO);
            balance.setCompensEarnedHours(BigDecimal.ZERO);
            balance.setCompensUsedHours(BigDecimal.ZERO);
        }
        return balance;
    }

    /** 管理端调整年假额度 */
    public void adjustQuota(Long employeeId, int year, BigDecimal quotaHours) {
        if (quotaHours == null || quotaHours.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "额度必须是非负数字");
        }
        ensureRow(employeeId, year);
        balanceMapper.updateQuota(employeeId, year, quotaHours);
    }

    private void ensureRow(Long employeeId, int year) {
        if (balanceMapper.selectOne(employeeId, year) == null) {
            balanceMapper.insertDefault(employeeId, year, defaultAnnualQuotaHours);
        }
    }
}
