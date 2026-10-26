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

    /** 工号唯一性校验，excludeId 用于排除自身（编辑场景） */
    long countByEmpNo(@Param("empNo") String empNo, @Param("excludeId") Long excludeId);

    /** 统计某部门下是否仍有在职员工，用于阻止删除部门 */
    long countActiveByDeptId(@Param("deptId") Long deptId);
}
