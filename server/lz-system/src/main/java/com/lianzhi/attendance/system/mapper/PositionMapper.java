package com.lianzhi.attendance.system.mapper;

import com.lianzhi.attendance.system.entity.Position;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 岗位数据访问。
 */
public interface PositionMapper {

    Position selectById(@Param("id") Long id);

    List<Position> selectByCompanyId(@Param("companyId") Long companyId);

    int insert(Position position);

    int updateById(Position position);

    long countReferencedByEmployee(@Param("positionId") Long positionId);
}
