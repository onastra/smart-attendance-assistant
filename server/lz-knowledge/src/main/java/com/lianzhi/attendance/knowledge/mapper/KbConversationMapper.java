package com.lianzhi.attendance.knowledge.mapper;

import com.lianzhi.attendance.knowledge.entity.KbConversation;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 会话持久化。所有读取都带 userId 条件，越权访问在 SQL 层就查不到。
 */
public interface KbConversationMapper {

    int insert(KbConversation conversation);

    KbConversation selectById(@Param("id") Long id);

    List<KbConversation> selectByUser(@Param("userId") Long userId, @Param("limit") int limit);

    int touch(@Param("id") Long id);

    int deleteById(@Param("id") Long id);
}
