package com.lianzhi.attendance.system.mapper;

import com.lianzhi.attendance.system.entity.Department;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 部门数据访问。
 */
public interface DepartmentMapper {

    Department selectById(@Param("id") Long id);

    List<Department> selectByCompanyId(@Param("companyId") Long companyId);

    List<Department> selectAll();

    int insert(Department dept);

    int updateById(Department dept);

    long countChildrenByParentId(@Param("parentId") Long parentId);
}
