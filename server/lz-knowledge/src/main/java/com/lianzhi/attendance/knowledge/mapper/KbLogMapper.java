package com.lianzhi.attendance.knowledge.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * 知识库操作日志（log_operation）。
 *
 * <p>上传/删除/重建都会改写知识库内容，属于需要留痕的写操作。</p>
 */
public interface KbLogMapper {

    int insertOperationLog(@Param("userId") Long userId,
                           @Param("module") String module,
                           @Param("action") String action,
                           @Param("targetId") String targetId,
                           @Param("success") int success,
                           @Param("message") String message);
}
