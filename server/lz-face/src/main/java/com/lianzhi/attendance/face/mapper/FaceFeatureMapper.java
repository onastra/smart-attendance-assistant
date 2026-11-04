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

    /**
     * 同 selectOne，但不过滤 status：供注册流程使用。
     * 注册必须能"复活"被管理员软删除（status=0）的记录——
     * 表上有 (employee_id, provider) 唯一索引，若只查有效记录后直接 INSERT，
     * 删除过注册的员工重新注册时会撞唯一索引报 500。
     */
    FaceFeature selectAny(@Param("employeeId") Long employeeId, @Param("provider") String provider);

    List<FaceFeature> selectPage(@Param("keyword") String keyword,
                                 @Param("offset") int offset,
                                 @Param("limit") int limit);

    long countPage(@Param("keyword") String keyword);

    /**
     * 停用该员工在所有识别引擎下的有效注册。
     * 注意不能按当前 provider 过滤：库里可能残留历史引擎（如 noop 模拟期）的记录，
     * 按 provider 过滤会导致名单上的旧引擎记录怎么删都删不掉。
     */
    int disable(@Param("employeeId") Long employeeId);
}
