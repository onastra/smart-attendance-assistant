package com.lianzhi.attendance.knowledge.mapper;

import com.lianzhi.attendance.knowledge.entity.KbMessage;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 会话消息持久化。
 */
public interface KbMessageMapper {

    int insert(KbMessage message);

    /** 会话内全部消息（按时间正序） */
    List<KbMessage> selectByConversation(@Param("conversationId") Long conversationId);

    /** 最近 N 条（用于拼装 LLM 上下文；返回按时间正序） */
    List<KbMessage> selectRecent(@Param("conversationId") Long conversationId,
                                 @Param("limit") int limit);

    int deleteByConversation(@Param("conversationId") Long conversationId);
}
