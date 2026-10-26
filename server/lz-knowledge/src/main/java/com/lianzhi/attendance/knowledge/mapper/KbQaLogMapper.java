package com.lianzhi.attendance.knowledge.mapper;

import com.lianzhi.attendance.knowledge.entity.KbQaLog;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 问答日志持久化。
 *
 * <p>只写"问了什么/命中几段/最高分/什么模式/耗时"，不写答案正文——
 * 见 {@link KbQaLog} 类注释。</p>
 */
public interface KbQaLogMapper {

    int insert(KbQaLog log);

    List<KbQaLog> selectRecent(@Param("userId") Long userId, @Param("limit") int limit);

    long countAll();
}
