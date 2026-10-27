package com.lianzhi.attendance.approval.service;

import com.lianzhi.attendance.approval.dto.ApplicationApproveRequest;
import com.lianzhi.attendance.approval.dto.ApplicationSubmitRequest;
import com.lianzhi.attendance.approval.entity.BizApplication;
import com.lianzhi.attendance.approval.entity.BizApplicationApproval;
import com.lianzhi.attendance.approval.mapper.ApplicationMapper;
import com.lianzhi.attendance.approval.query.ApplicationQuery;
import com.lianzhi.attendance.common.exception.BusinessException;
import com.lianzhi.attendance.common.page.PageResult;
import com.lianzhi.attendance.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;

/**
 * 请假/加班审批业务。
 *
 * <p>审批为单级：提交后任意持 application:approve 权限者均可处理。
 * 状态机保证终态（APPROVED/REJECTED/CANCELLED）不可重复审批（REQ-E-06）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationService {

    private static final DateTimeFormatter NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final Random random = new Random();

    private final ApplicationMapper mapper;

    /**
     * 提交申请（REQ-E-01）。
     */
    @Transactional
    public BizApplication submit(ApplicationSubmitRequest req, Long employeeId) {
        if (employeeId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请先在员工档案中绑定本人后再提交申请");
        }
        if (!BizApplication.TYPE_LEAVE.equals(req.getType())
                && !BizApplication.TYPE_OVERTIME.equals(req.getType())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "申请类型只能是 LEAVE 或 OVERTIME");
        }
        if (req.getEndTime() == null || req.getStartTime() == null
                || !req.getEndTime().isAfter(req.getStartTime())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "结束时间必须晚于开始时间");
        }
        String reason = req.getReason() == null ? "" : req.getReason().trim();
        if (reason.length() < 2) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "事由至少需要 2 个字");
        }

        BizApplication app = new BizApplication();
        app.setApplicationNo(generateNo());
        app.setEmployeeId(employeeId);
        app.setType(req.getType());
        app.setSubType(req.getSubType() == null ? null : req.getSubType().trim());
        app.setStartTime(req.getStartTime());
        app.setEndTime(req.getEndTime());
        app.setDurationHours(durationHours(req.getStartTime(), req.getEndTime()));
        app.setReason(reason);
        app.setAttachmentFileId(req.getAttachmentFileId());
        app.setStatus(BizApplication.STATUS_SUBMITTED);
        app.setSubmittedAt(LocalDateTime.now());
        mapper.insert(app);

        log.info("[APP] 提交申请 no={} employeeId={} type={}", app.getApplicationNo(), employeeId, req.getType());
        return mapper.selectById(app.getId());
    }

    /**
     * 我的申请（REQ-E-02）：仅本人，employeeId 由服务层强制填入，前端无法越权查看他人。
     */
    public PageResult<BizApplication> pageMine(ApplicationQuery query, Long employeeId) {
        if (employeeId == null) {
            return PageResult.empty(query);
        }
        query.setEmployeeId(employeeId);
        return doPage(query);
    }

    /**
     * 待审批队列（REQ-E-03）：返回 SUBMITTED 状态，供持 application:approve 者处理。
     */
    public PageResult<BizApplication> pageTodo(ApplicationQuery query) {
        query.setStatus(BizApplication.STATUS_SUBMITTED);
        query.setEmployeeId(null);
        return doPage(query);
    }

    /**
     * 全部申请（REQ-E-05，管理员监督，需 application:list）。
     */
    public PageResult<BizApplication> pageAll(ApplicationQuery query) {
        query.setEmployeeId(null);
        return doPage(query);
    }

    /**
     * 审批（REQ-E-03 / REQ-E-04）：通过或驳回，驳回必须填意见；终态不可重复审批。
     */
    @Transactional
    public void approve(Long id, ApplicationApproveRequest req, Long approverId) {
        BizApplication app = mapper.selectById(id);
        if (app == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "申请单不存在");
        }
        if (!BizApplication.STATUS_SUBMITTED.equals(app.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "该申请单已处理（" + app.getStatus() + "），不能重复审批");
        }
        String action = req.getAction();
        if (!BizApplicationApproval.ACTION_APPROVE.equals(action)
                && !BizApplicationApproval.ACTION_REJECT.equals(action)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "审批动作只能是 APPROVE 或 REJECT");
        }
        if (BizApplicationApproval.ACTION_REJECT.equals(action)
                && (req.getComment() == null || req.getComment().trim().isEmpty())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "驳回必须填写审批意见");
        }

        String newStatus = BizApplicationApproval.ACTION_APPROVE.equals(action)
                ? BizApplication.STATUS_APPROVED
                : BizApplication.STATUS_REJECTED;
        mapper.updateOnApprove(id, newStatus, approverId, LocalDateTime.now());

        BizApplicationApproval approval = new BizApplicationApproval();
        approval.setApplicationId(id);
        approval.setApproverId(approverId);
        approval.setAction(action);
        approval.setComment(req.getComment() == null ? null : req.getComment().trim());
        mapper.insertApproval(approval);

        log.info("[APP] 审批 id={} action={} approver={}", id, action, approverId);
    }

    /**
     * 撤回申请（申请人本人，仅 SUBMITTED 可撤）。
     */
    @Transactional
    public void cancel(Long id, Long employeeId) {
        BizApplication app = mapper.selectById(id);
        if (app == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "申请单不存在");
        }
        if (app.getEmployeeId() == null || !app.getEmployeeId().equals(employeeId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能撤回本人的申请");
        }
        if (!BizApplication.STATUS_SUBMITTED.equals(app.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "仅待审批的申请可以撤回");
        }
        mapper.updateStatus(id, BizApplication.STATUS_CANCELLED);
        log.info("[APP] 撤回申请 id={} employeeId={}", id, employeeId);
    }

    /**
     * E-07 集成点：一段时间范围内已通过的申请，供统计模块读取后在考勤中体现。
     * 注意：当前统计重算尚未消费本方法，避免静默改动已稳定的统计模块；本方法仅为预留接入点。
     */
    public List<BizApplication> listApprovedInRange(LocalDateTime start, LocalDateTime end) {
        return mapper.selectApprovedInRange(start, end);
    }

    private PageResult<BizApplication> doPage(ApplicationQuery query) {
        long total = mapper.countByQuery(query);
        if (total == 0) {
            return PageResult.empty(query);
        }
        return PageResult.of(query, total, mapper.selectByQuery(query));
    }

    private String generateNo() {
        // LA + 时间(14位) + 纳秒取模(6位)，几乎不可能重复；application_no 有唯一约束兜底
        String suffix = String.format("%06d", Math.abs(System.nanoTime() % 1_000_000));
        return "LA" + LocalDateTime.now().format(NO_TIME) + suffix;
    }

    private BigDecimal durationHours(LocalDateTime start, LocalDateTime end) {
        long minutes = Duration.between(start, end).toMinutes();
        return BigDecimal.valueOf(minutes)
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }
}
