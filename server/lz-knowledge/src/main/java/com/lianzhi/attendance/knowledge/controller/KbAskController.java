package com.lianzhi.attendance.knowledge.controller;

import com.lianzhi.attendance.common.result.R;
import com.lianzhi.attendance.framework.auth.AuthContext;
import com.lianzhi.attendance.framework.auth.AuthUser;
import com.lianzhi.attendance.framework.auth.RequirePermission;
import com.lianzhi.attendance.knowledge.agent.AgentOrchestrator;
import com.lianzhi.attendance.knowledge.dto.AskRequest;
import com.lianzhi.attendance.knowledge.dto.AskResponse;
import com.lianzhi.attendance.knowledge.llm.LlmClient;
import com.lianzhi.attendance.knowledge.service.KnowledgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库问答接口。
 */
@Tag(name = "知识库问答", description = "自然语言提问，返回答案、来源引用与当前运行模式")
@RestController
@RequiredArgsConstructor
public class KbAskController {

    private final AgentOrchestrator orchestrator;
    private final KnowledgeService knowledgeService;
    private final List<LlmClient> llmClients;

    @Operation(summary = "提问", description = "响应含 mode（retrieval-only / llm-augmented）与 grounded；未命中时明确告知，不编造答案")
    @PostMapping("/api/kb/ask")
    @RequirePermission("kb:ask")
    public R<AskResponse> ask(@Valid @RequestBody AskRequest request) {
        AuthUser user = AuthContext.get();
        AskResponse response = orchestrator.ask(
                request.getQuestion().trim(), user, request.getDocumentId(), request.getTopK());
        return R.ok(response);
    }

    /**
     * 当前生成能力状态。
     *
     * <p>前端用它决定要不要展示"未配置大模型"的提示条——
     * 把这件事做成接口而不是前端写死，避免两边说法不一致。</p>
     */
    @Operation(summary = "生成能力状态", description = "返回当前是否有可用的大模型；未配置时给出降级说明")
    @GetMapping("/api/kb/llm-status")
    @RequirePermission("kb:ask")
    public R<Map<String, Object>> llmStatus() {
        LlmClient active = llmClients.stream().filter(LlmClient::available).findFirst().orElse(null);
        Map<String, Object> data = new HashMap<>();
        data.put("mode", active == null ? AgentOrchestrator.MODE_RETRIEVAL_ONLY
                : AgentOrchestrator.MODE_LLM_AUGMENTED);
        data.put("available", active != null);
        data.put("model", active == null ? null : active.name());
        data.put("notice", active == null ? defaultNotice() : null);
        return R.ok(data);
    }

    @Operation(summary = "问答日志", description = "只含问题原文与检索统计，不含答案正文")
    @GetMapping("/api/kb/qa-logs")
    @RequirePermission("kb:qa:log")
    public R<Map<String, Object>> logs(@RequestParam(value = "userId", required = false) Long userId,
                                       @RequestParam(value = "limit", defaultValue = "20") int limit) {
        Map<String, Object> data = new HashMap<>();
        data.put("list", knowledgeService.recentLogs(userId, Math.min(limit, 100)));
        return R.ok(data);
    }

    private String defaultNotice() {
        return llmClients.isEmpty() ? "未配置大模型服务，当前为检索模式（retrieval-only）。"
                : llmClients.get(llmClients.size() - 1).unavailableReason();
    }
}
