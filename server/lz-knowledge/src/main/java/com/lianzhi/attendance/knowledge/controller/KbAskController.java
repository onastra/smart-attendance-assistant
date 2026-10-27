package com.lianzhi.attendance.knowledge.controller;

import com.lianzhi.attendance.common.result.R;
import com.lianzhi.attendance.framework.auth.AuthContext;
import com.lianzhi.attendance.framework.auth.AuthUser;
import com.lianzhi.attendance.framework.auth.RequirePermission;
import com.lianzhi.attendance.knowledge.agent.AgentOrchestrator;
import com.lianzhi.attendance.knowledge.dto.AskRequest;
import com.lianzhi.attendance.knowledge.dto.AskResponse;
import com.lianzhi.attendance.knowledge.llm.LlmClient;
import com.lianzhi.attendance.knowledge.retrieve.Retriever;
import com.lianzhi.attendance.knowledge.service.KnowledgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 知识库问答接口。
 */
@Slf4j
@Tag(name = "知识库问答", description = "自然语言提问，返回答案、来源引用与当前运行模式；支持多轮会话与流式输出")
@RestController
@RequiredArgsConstructor
public class KbAskController {

    private final AgentOrchestrator orchestrator;
    private final KnowledgeService knowledgeService;
    private final List<LlmClient> llmClients;
    private final Retriever retriever;

    /** 流式问答的执行线程池：守护线程，JVM 退出不阻塞 */
    private final ExecutorService streamExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "kb-ask-stream");
        t.setDaemon(true);
        return t;
    });

    @Operation(summary = "提问", description = "响应含 mode（retrieval-only / llm-augmented）与 grounded；未命中时明确告知，不编造答案；可携带 conversationId 进行多轮对话")
    @PostMapping("/api/kb/ask")
    @RequirePermission("kb:ask")
    public R<AskResponse> ask(@Valid @RequestBody AskRequest request) {
        AuthUser user = AuthContext.get();
        AskResponse response = orchestrator.ask(
                request.getQuestion().trim(), user, request.getDocumentId(), request.getTopK(),
                request.getConversationId());
        return R.ok(response);
    }

    /**
     * 流式提问（SSE）。
     *
     * <p>事件序列：{@code meta}（来源与模式）→ 若干 {@code delta}（增量文本）→
     * {@code done}（最终响应）或 {@code error}。POST + SSE 是因为提问体在 body 里，
     * GET EventSource 放不下；前端用 fetch 读取事件流。</p>
     */
    @Operation(summary = "流式提问", description = "SSE 事件流：meta → delta* → done/error")
    @PostMapping(value = "/api/kb/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequirePermission("kb:ask")
    public SseEmitter askStream(@Valid @RequestBody AskRequest request) {
        AuthUser user = AuthContext.get();
        SseEmitter emitter = new SseEmitter(180_000L);
        streamExecutor.execute(() -> {
            orchestrator.askStream(
                    request.getQuestion().trim(), user, request.getDocumentId(), request.getTopK(),
                    request.getConversationId(),
                    new AgentOrchestrator.StreamHandler() {
                        @Override
                        public void onMeta(AskResponse meta) {
                            send(emitter, "meta", meta);
                        }

                        @Override
                        public void onDelta(String delta) {
                            send(emitter, "delta", delta);
                        }

                        @Override
                        public void onComplete(AskResponse response) {
                            send(emitter, "done", response);
                            emitter.complete();
                        }

                        @Override
                        public void onError(String message) {
                            send(emitter, "error", Map.of("message", message));
                            emitter.complete();
                        }
                    });
        });
        return emitter;
    }

    private static void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            // 客户端断开连接：静默收尾，流的另一端已经不在乎了
            log.debug("[KB] SSE 发送失败（客户端可能已断开）：{}", e.getMessage());
        }
    }

    /**
     * 当前生成能力状态。
     *
     * <p>前端用它决定要不要展示"未配置大模型"的提示条——
     * 把这件事做成接口而不是前端写死，避免两边说法不一致。</p>
     */
    @Operation(summary = "生成能力状态", description = "返回当前是否有可用的大模型与语义检索；未配置时给出降级说明")
    @GetMapping("/api/kb/llm-status")
    @RequirePermission("kb:ask")
    public R<Map<String, Object>> llmStatus() {
        LlmClient active = llmClients.stream().filter(LlmClient::available).findFirst().orElse(null);
        Map<String, Object> data = new HashMap<>();
        data.put("mode", active == null ? AgentOrchestrator.MODE_RETRIEVAL_ONLY
                : AgentOrchestrator.MODE_LLM_AUGMENTED);
        data.put("available", active != null);
        data.put("model", active == null ? null : active.name());
        data.put("vectorEnabled", retriever.vectorEnabled());
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
