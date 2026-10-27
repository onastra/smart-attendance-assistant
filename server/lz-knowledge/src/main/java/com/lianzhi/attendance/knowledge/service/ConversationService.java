package com.lianzhi.attendance.knowledge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lianzhi.attendance.common.exception.BusinessException;
import com.lianzhi.attendance.common.result.ResultCode;
import com.lianzhi.attendance.framework.auth.AuthUser;
import com.lianzhi.attendance.knowledge.dto.ChatTurn;
import com.lianzhi.attendance.knowledge.dto.ChunkHit;
import com.lianzhi.attendance.knowledge.entity.KbConversation;
import com.lianzhi.attendance.knowledge.entity.KbMessage;
import com.lianzhi.attendance.knowledge.mapper.KbConversationMapper;
import com.lianzhi.attendance.knowledge.mapper.KbMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 多轮会话服务：会话归属校验、消息存取、历史上下文拼装。
 *
 * <p>会话是会话，审计是审计：kb_qa_log 继续只记问题与统计（不变）；
 * 这里的消息表是用户自己的对话历史，读取与删除都强制本人操作。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    /** 新会话标题取首条问题的前 20 字 */
    private static final int TITLE_LENGTH = 20;

    /** 传给 LLM 的历史消息数上限：再多会稀释"只依据资料片段"的约束 */
    private static final int HISTORY_LIMIT = 10;

    private final KbConversationMapper conversationMapper;
    private final KbMessageMapper messageMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 解析本次提问归属的会话：conversationId 为空则新建，非空则校验归属。
     *
     * @return 会话（持久化后的，含 ID）
     */
    public KbConversation resolve(Long conversationId, String question, AuthUser user) {
        Long userId = user == null ? null : user.getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "登录信息缺失，无法使用会话功能");
        }
        if (conversationId == null) {
            KbConversation conversation = new KbConversation();
            conversation.setUserId(userId);
            conversation.setTitle(makeTitle(question));
            conversationMapper.insert(conversation);
            return conversation;
        }
        KbConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "会话不存在或已删除");
        }
        if (!conversation.getUserId().equals(userId)) {
            // 不能泄露"会话存在但不是你的"这一事实，统一报不存在
            throw new BusinessException(ResultCode.NOT_FOUND, "会话不存在或已删除");
        }
        return conversation;
    }

    /** 取最近若干轮历史（正序），供 LLM 做上下文 */
    public List<ChatTurn> recentHistory(Long conversationId) {
        return messageMapper.selectRecent(conversationId, HISTORY_LIMIT).stream()
                .map(m -> new ChatTurn(m.getRole(), m.getContent()))
                .toList();
    }

    /** 落一条用户消息 */
    public KbMessage appendUserMessage(Long conversationId, String question) {
        KbMessage message = new KbMessage();
        message.setConversationId(conversationId);
        message.setRole(KbMessage.ROLE_USER);
        message.setContent(question);
        messageMapper.insert(message);
        conversationMapper.touch(conversationId);
        return message;
    }

    /** 落一条助手消息（含来源快照与运行模式） */
    public KbMessage appendAssistantMessage(Long conversationId, String answer,
                                            List<ChunkHit> sources, String mode) {
        KbMessage message = new KbMessage();
        message.setConversationId(conversationId);
        message.setRole(KbMessage.ROLE_ASSISTANT);
        message.setContent(answer);
        message.setMode(mode);
        message.setSources(sourcesSnapshot(sources));
        messageMapper.insert(message);
        conversationMapper.touch(conversationId);
        return message;
    }

    public List<KbConversation> listMine(AuthUser user, int limit) {
        Long userId = user == null ? null : user.getUserId();
        if (userId == null) {
            return List.of();
        }
        return conversationMapper.selectByUser(userId, Math.min(limit, 100));
    }

    public List<KbMessage> messages(Long conversationId, AuthUser user) {
        requireMine(conversationId, user);
        return messageMapper.selectByConversation(conversationId);
    }

    /** 删除会话及其全部消息；只允许本人 */
    public void delete(Long conversationId, AuthUser user) {
        requireMine(conversationId, user);
        messageMapper.deleteByConversation(conversationId);
        conversationMapper.deleteById(conversationId);
    }

    private void requireMine(Long conversationId, AuthUser user) {
        KbConversation conversation = conversationMapper.selectById(conversationId);
        Long userId = user == null ? null : user.getUserId();
        if (conversation == null || userId == null || !conversation.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "会话不存在或已删除");
        }
    }

    /**
     * 来源快照只存展示所需字段（标题/位置/摘录），不存切片全文：
     * 消息表是历史记录，不是第二份知识库副本。
     */
    private String sourcesSnapshot(List<ChunkHit> sources) {
        if (sources == null || sources.isEmpty()) {
            return null;
        }
        try {
            List<Map<String, Object>> snapshot = sources.stream().map(hit -> {
                Map<String, Object> item = new HashMap<>();
                item.put("chunkId", hit.getChunkId());
                item.put("documentId", hit.getDocumentId());
                item.put("documentTitle", hit.getDocumentTitle());
                item.put("section", hit.getSection());
                item.put("excerpt", hit.getExcerpt());
                item.put("matchType", hit.getMatchType());
                return item;
            }).toList();
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            log.warn("[KB] 来源快照序列化失败：{}", e.getMessage());
            return null;
        }
    }

    private static String makeTitle(String question) {
        if (question == null || question.isBlank()) {
            return "新会话";
        }
        String trimmed = question.trim();
        return trimmed.length() <= TITLE_LENGTH ? trimmed : trimmed.substring(0, TITLE_LENGTH) + "…";
    }
}
