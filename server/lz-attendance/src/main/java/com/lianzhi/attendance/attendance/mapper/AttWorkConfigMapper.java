package com.lianzhi.attendance.attendance.mapper;

import com.lianzhi.attendance.attendance.entity.AttWorkConfig;
import org.apache.ibatis.annotations.Param;

/**
 * 全局考勤时间配置数据访问。
 */
public interface AttWorkConfigMapper {

    /** 读取单行配置；表为空时返回 null（调用方回退 yml 默认值） */
    AttWorkConfig selectOne();

    int update(@Param("c") AttWorkConfig config);
}
