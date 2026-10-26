package com.lianzhi.attendance.face.mapper;

import com.lianzhi.attendance.face.entity.FaceFeature;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 人脸特征持久化。
 */
public interface FaceFeatureMapper {

    int insert(FaceFeature feature);

    int updateFeature(@Param("employeeId") Long employeeId,
                      @Param("provider") String provider,
                      @Param("featureHash") String featureHash,
                      @Param("mockFlag") Integer mockFlag);

    FaceFeature selectOne(@Param("employeeId") Long employeeId, @Param("provider") String provider);

    List<FaceFeature> selectPage(@Param("keyword") String keyword,
                                 @Param("offset") int offset,
                                 @Param("limit") int limit);

    long countPage(@Param("keyword") String keyword);

    int disable(@Param("employeeId") Long employeeId, @Param("provider") String provider);
}
