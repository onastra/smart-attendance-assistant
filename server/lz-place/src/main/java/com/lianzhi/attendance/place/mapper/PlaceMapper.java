package com.lianzhi.attendance.place.mapper;

import com.lianzhi.attendance.place.entity.Place;
import com.lianzhi.attendance.place.entity.PlaceEmployee;
import com.lianzhi.attendance.place.query.PlaceQuery;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 办公场所数据访问。
 */
public interface PlaceMapper {

    Place selectById(@Param("id") Long id);

    List<Place> selectByQuery(PlaceQuery query);

    long countByQuery(PlaceQuery query);

    int insert(Place place);

    int updateById(Place place);

    long countByPlaceCode(@Param("placeCode") String placeCode, @Param("excludeId") Long excludeId);

    List<PlaceEmployee> selectEmployeesByPlaceId(@Param("placeId") Long placeId);

    /** 判断员工是否存在于档案表（跨模块只读校验，同一库的不同物理表） */
    long countEmployeeById(@Param("employeeId") Long employeeId);

    long countBinding(@Param("employeeId") Long employeeId, @Param("placeId") Long placeId);

    int insertBinding(@Param("employeeId") Long employeeId, @Param("placeId") Long placeId);

    int disableBinding(@Param("employeeId") Long employeeId, @Param("placeId") Long placeId);

    /** 覆盖已绑定员工（全量语义） */
    int disableAllBindings(@Param("placeId") Long placeId);

    long countActiveBindingsByPlaceId(@Param("placeId") Long placeId);
}
