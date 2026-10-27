package com.lianzhi.attendance.approval.mapper;

import com.lianzhi.attendance.approval.entity.BizApplication;
import com.lianzhi.attendance.approval.entity.BizApplicationApproval;
import com.lianzhi.attendance.approval.query.ApplicationQuery;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 请假/加班申请数据访问。
 */
public interface ApplicationMapper {

    int insert(BizApplication application);

    BizApplication selectById(@Param("id") Long id);

    List<BizApplication> selectByQuery(ApplicationQuery query);

    long countByQuery(ApplicationQuery query);

    /** 审批落库：更新状态、当前审批人、完成时间 */
    int updateOnApprove(@Param("id") Long id,
                        @Param("status") String status,
                        @Param("approverId") Long approverId,
                        @Param("finishedAt") LocalDateTime finishedAt);

    /** 撤回等简单状态变更 */
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    int insertApproval(BizApplicationApproval approval);

    List<BizApplicationApproval> selectApprovals(@Param("applicationId") Long applicationId);

    /**
     * E-07 集成点：一段时间范围内已通过的申请，供统计模块读取后在考勤中体现。
     */
    List<BizApplication> selectApprovedInRange(@Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end);
}
