package com.lianzhi.attendance.knowledge.mapper;

import com.lianzhi.attendance.knowledge.entity.SysFileRecord;
import org.apache.ibatis.annotations.Param;

/**
 * 上传文件记录持久化。
 */
public interface KbFileMapper {

    int insert(SysFileRecord file);

    SysFileRecord selectById(@Param("id") Long id);

    int deleteById(@Param("id") Long id);
}
