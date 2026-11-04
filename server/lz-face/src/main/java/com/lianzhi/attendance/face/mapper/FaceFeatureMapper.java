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
     * 注册用 selectAny 判断是否已有记录（含任何历史遗留行），
     * 有则走 UPDATE 路线而非 INSERT——表上有 (employee_id, provider) 唯一索引，
     * 直接 INSERT 可能撞唯一索引报 500。
     */
    FaceFeature selectAny(@Param("employeeId") Long employeeId, @Param("provider") String provider);

    List<FaceFeature> selectPage(@Param("keyword") String keyword,
                                 @Param("offset") int offset,
                                 @Param("limit") int limit);

    long countPage(@Param("keyword") String keyword);

    /**
     * 物理删除该员工在所有识别引擎下的人脸注册记录。
     * 注意不能按当前 provider 过滤：库里可能残留历史引擎（如 noop 模拟期）的记录，
     * 按 provider 过滤会导致名单上的旧引擎记录怎么删都删不掉。
     */
    int deleteByEmployeeId(@Param("employeeId") Long employeeId);
}
