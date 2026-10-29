package com.lianzhi.attendance.approval.mapper;

import com.lianzhi.attendance.approval.entity.LeaveBalance;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

/**
 * 请假/调休额度数据访问（阶段 F）。
 */
public interface LeaveBalanceMapper {

    /** 查某人某年额度行；不存在返回 null（调用方决定是否初始化） */
    LeaveBalance selectOne(@Param("employeeId") Long employeeId, @Param("year") int year);

    /** 初始化默认额度行（年假 40h），依赖 uk_balance_emp_year 唯一键防重 */
    int insertDefault(@Param("employeeId") Long employeeId, @Param("year") int year,
                      @Param("quotaHours") BigDecimal quotaHours);

    /** 已批请假：累加已用（年假/事假/病假） */
    int addLeaveUsed(@Param("employeeId") Long employeeId, @Param("year") int year,
                     @Param("hours") BigDecimal hours);

    /** 已批调休：累加已用调休（调用方须先校验余额） */
    int addCompensUsed(@Param("employeeId") Long employeeId, @Param("year") int year,
                       @Param("hours") BigDecimal hours);

    /** 已批加班：累加折算调休额度 */
    int addCompensEarned(@Param("employeeId") Long employeeId, @Param("year") int year,
                         @Param("hours") BigDecimal hours);

    /** 管理端调整年假额度（直接设值，不用累加，便于纠正演示数据） */
    int updateQuota(@Param("employeeId") Long employeeId, @Param("year") int year,
                    @Param("quotaHours") BigDecimal quotaHours);

    /** 带员工姓名与余额派生字段的单行查询 */
    LeaveBalance selectWithBalance(@Param("employeeId") Long employeeId, @Param("year") int year);
}
