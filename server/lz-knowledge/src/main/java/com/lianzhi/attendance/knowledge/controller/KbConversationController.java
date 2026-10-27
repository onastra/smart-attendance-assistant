package com.lianzhi.attendance.knowledge.controller;

import com.lianzhi.attendance.common.result.R;
import com.lianzhi.attendance.framework.auth.AuthContext;
import com.lianzhi.attendance.framework.auth.AuthUser;
import com.lianzhi.attendance.framework.auth.RequirePermission;
import com.lianzhi.attendance.knowledge.entity.KbConversation;
import com.lianzhi.attendance.knowledge.entity.KbMessage;
import com.lianzhi.attendance.knowledge.service.ConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话管理接口：历史会话列表、消息记录、删除。
 *
 * <p>所有操作只允许本人：归属校验在 service 层强制，越权统一报"不存在"。</p>
 */
@Tag(name = "知识库会话", description = "多轮对话的会话列表、消息历史与删除")
@RestController
@RequiredArgsConstructor
public class KbConversationController {

    private final ConversationService conversationService;

    @Operation(summary = "我的会话列表", description = "按最近活跃倒序")
    @GetMapping("/api/kb/conversations")
    @RequirePermission("kb:ask")
    public R<Map<String, Object>> list(@RequestParam(value = "limit", defaultValue = "50") int limit) {
        AuthUser user = AuthContext.get();
        List<KbConversation> conversations = conversationService.listMine(user, limit);
        Map<String, Object> data = new HashMap<>();
        data.put("list", conversations);
        return R.ok(data);
    }

    @Operation(summary = "会话消息记录", description = "按时间正序返回全部消息；assistant 消息含来源快照")
    @GetMapping("/api/kb/conversations/{id}/messages")
    @RequirePermission("kb:ask")
    public R<Map<String, Object>> messages(@PathVariable("id") Long id) {
        AuthUser user = AuthContext.get();
        List<KbMessage> messages = conversationService.messages(id, user);
        Map<String, Object> data = new HashMap<>();
        data.put("list", messages);
        return R.ok(data);
    }

    @Operation(summary = "删除会话", description = "连同消息一起删除；只允许本人操作")
    @DeleteMapping("/api/kb/conversations/{id}")
    @RequirePermission("kb:ask")
    public R<Void> delete(@PathVariable("id") Long id) {
        AuthUser user = AuthContext.get();
        conversationService.delete(id, user);
        return R.ok(null);
    }
}
