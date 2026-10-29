package com.lianzhi.attendance.system.mapper;

import com.lianzhi.attendance.system.entity.Employee;
import com.lianzhi.attendance.system.query.EmployeeQuery;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 员工档案数据访问。
 */
public interface EmployeeMapper {

    Employee selectById(@Param("id") Long id);

    List<Employee> selectByQuery(EmployeeQuery query);

    long countByQuery(EmployeeQuery query);

    int insert(Employee employee);

    int updateById(Employee employee);

    /** 离职 = 物理删除：先清关联数据再删档案行，全部在 Service 的事务里调用 */
    int deleteById(@Param("id") Long id);

    int deleteFaceByEmployeeId(@Param("id") Long id);

    int deleteApplicationByEmployeeId(@Param("id") Long id);

    int deleteDailyStatByEmployeeId(@Param("id") Long id);

    int deleteAttRecordByEmployeeId(@Param("id") Long id);

    int deleteEmployeePlaceByEmployeeId(@Param("id") Long id);

    /** 员工删除后，一并删除其登录账号的角色关联（账号与档案同生共死） */
    int deleteUserRolesByEmployeeId(@Param("id") Long id);

    /** 员工删除后，一并删除其登录账号 */
    int deleteUserByEmployeeId(@Param("id") Long id);

    /** 员工删除后，不再担任任何部门的负责人 */
    int clearDeptLeaderByEmployeeId(@Param("id") Long id);

    /** 工号唯一性校验，excludeId 用于排除自身（编辑场景） */
    long countByEmpNo(@Param("empNo") String empNo, @Param("excludeId") Long excludeId);

    /** 统计某部门下是否仍有在职员工，用于阻止删除部门 */
    long countActiveByDeptId(@Param("deptId") Long deptId);
}
