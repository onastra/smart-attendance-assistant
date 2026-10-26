package com.lianzhi.attendance.system.mapper;

import com.lianzhi.attendance.system.entity.Company;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 公司数据访问。
 */
public interface CompanyMapper {

    Company selectById(@Param("id") Long id);

    List<Company> selectAll();

    int insert(Company company);

    int updateById(Company company);

    long countByCode(@Param("companyCode") String companyCode, @Param("excludeId") Long excludeId);

    long countChildrenByParentId(@Param("parentId") Long parentId);
}
