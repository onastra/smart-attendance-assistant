package com.lianzhi.attendance.knowledge.agent;

import com.lianzhi.attendance.common.exception.BusinessException;
import com.lianzhi.attendance.framework.auth.AuthUser;
import com.lianzhi.attendance.knowledge.dto.AskResponse;
import com.lianzhi.attendance.knowledge.dto.ChatTurn;
import com.lianzhi.attendance.knowledge.dto.ChunkHit;
import com.lianzhi.attendance.knowledge.entity.KbConversation;
import com.lianzhi.attendance.knowledge.entity.KbMessage;
import com.lianzhi.attendance.knowledge.llm.LlmClient;
import com.lianzhi.attendance.knowledge.mapper.KbQaLogMapper;
import com.lianzhi.attendance.knowledge.entity.KbQaLog;
import com.lianzhi.attendance.knowledge.retrieve.Retriever;
import com.lianzhi.attendance.knowledge.service.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 问答编排：会话解析 → 检索 →（可选）生成 → 落消息与日志。
 *
 * <p>分层是刻意的（需求 IDX-F7）：</p>
 * <pre>
 *   AgentOrchestrator  ← 只管流程与降级，不含任何检索/HTTP 细节
 *        ├── ConversationService ← 会话归属、消息存取、历史上下文
 *        ├── Retriever           ← 只管"取回有权阅读的相关片段"（全文+向量混合召回）
 *        └── LlmClient           ← 只管"在给定片段上组织语言"，可整体替换或缺失
 * </pre>
 * 每一层都能单独构造与单测，换模型不影响检索，换检索不影响模型。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentOrchestrator {

    public static final String MODE_RETRIEVAL_ONLY = "retrieval-only";
    public static final String MODE_LLM_AUGMENTED = "llm-augmented";

    private static final String NO_HIT_ANSWER =
            "未找到足够依据，无法回答。知识库中没有与问题相关的可读内容，"
                    + "为避免给出不准确的答复，这里不做推测。";

    private final Retriever retriever;
    private final List<LlmClient> llmClients;
    private final KbQaLogMapper qaLogMapper;
    private final ConversationService conversationService;

    /** 流式输出的回调接口：编排层不依赖 Spring MVC 的 SseEmitter，保持可单测 */
    public interface StreamHandler {
        /** 生成开始前：来源、模式、会话 ID 等元信息 */
        void onMeta(AskResponse meta);

        /** 一段增量文本 */
        void onDelta(String delta);

        /** 正常结束：携带最终响应（含消息 ID、耗时） */
        void onComplete(AskResponse response);

        /** 中途失败：已发出的增量由调用方自行标注"不完整" */
        void onError(String message);
    }

    /** 兼容旧签名：不带会话的单轮问答 */
    public AskResponse ask(String question, AuthUser user, Long documentId, Integer topK) {
        return ask(question, user, documentId, topK, null);
    }

    public AskResponse ask(String question, AuthUser user, Long documentId, Integer topK,
                           Long conversationId) {
        long start = System.currentTimeMillis();
        KbConversation conversation = conversationService.resolve(conversationId, question, user);
        List<ChatTurn> history = conversationService.recentHistory(conversation.getId());
        conversationService.appendUserMessage(conversation.getId(), question);

        AskResponse response = doAsk(question, user, documentId, topK, history);
        persistAssistant(conversation.getId(), response);
        finish(response, user, start);
        return response;
    }

    /**
     * 流式问答。流程与非流式一致，区别只在生成阶段逐段回调。
     *
     * <p>降级纪律：流式调用中途失败时，<strong>已经吐出去的增量不回收也不补救</strong>
     * ——直接 onError 让前端标注"回答不完整"，而不是悄悄换成检索模式的整段答案
     * 接在后面冒充模型输出。</p>
     */
    public void askStream(String question, AuthUser user, Long documentId, Integer topK,
                          Long conversationId, StreamHandler handler) {
        long start = System.currentTimeMillis();
        AskResponse response = new AskResponse();
        try {
            KbConversation conversation = conversationService.resolve(conversationId, question, user);
            List<ChatTurn> history = conversationService.recentHistory(conversation.getId());
            conversationService.appendUserMessage(conversation.getId(), question);
            response.setConversationId(conversation.getId());

            List<ChunkHit> hits = retriever.retrieve(question, user, documentId, topK);
            response.setQuestion(question);
            response.setSources(hits);
            response.setTopScore(topScoreOf(hits));

            if (hits.isEmpty()) {
                // 第一道闸：检索层就没命中，绝不允许模型凭印象作答
                response.setGrounded(false);
                response.setReason("NO_HIT");
                response.setAnswer(NO_HIT_ANSWER);
                response.setMode(MODE_RETRIEVAL_ONLY);
                handler.onMeta(response);
                handler.onDelta(NO_HIT_ANSWER);
                persistAssistant(conversation.getId(), response);
                finish(response, user, start);
                handler.onComplete(response);
                return;
            }

            LlmClient llm = pickLlm();
            if (llm == null || !llm.available()) {
                fillRetrievalAnswer(response, hits);
                response.setLlmNotice(llm == null ? defaultNotice() : llm.unavailableReason());
                handler.onMeta(response);
                handler.onDelta(response.getAnswer());
                persistAssistant(conversation.getId(), response);
                finish(response, user, start);
                handler.onComplete(response);
                return;
            }

            response.setGrounded(true);
            response.setMode(MODE_LLM_AUGMENTED);
            response.setModel(llm.name());
            handler.onMeta(response);

            StringBuilder answer = new StringBuilder();
            llm.generateStream(question, hits, history, delta -> {
                answer.append(delta);
                handler.onDelta(delta);
            });
            response.setAnswer(answer.toString());
            persistAssistant(conversation.getId(), response);
            finish(response, user, start);
            handler.onComplete(response);
        } catch (BusinessException e) {
            log.warn("[KB] 流式问答失败：{}", e.getMessage());
            response.setLlmNotice("生成失败：" + e.getMessage());
            handler.onError(e.getMessage());
        } catch (Exception e) {
            log.warn("[KB] 流式问答异常", e);
            handler.onError("问答处理失败：" + e.getMessage());
        }
    }

    /** 检索 + 生成的核心流程（非流式），产出尚未落库的响应 */
    private AskResponse doAsk(String question, AuthUser user, Long documentId, Integer topK,
                              List<ChatTurn> history) {
        AskResponse response = new AskResponse();
        response.setQuestion(question);

        List<ChunkHit> hits = retriever.retrieve(question, user, documentId, topK);
        response.setSources(hits);
        response.setTopScore(topScoreOf(hits));

        if (hits.isEmpty()) {
            response.setGrounded(false);
            response.setReason("NO_HIT");
            response.setAnswer(NO_HIT_ANSWER);
            response.setMode(MODE_RETRIEVAL_ONLY);
            response.setLlmNotice(defaultNotice());
            return response;
        }

        LlmClient llm = pickLlm();
        if (llm != null && llm.available()) {
            try {
                response.setAnswer(llm.generate(question, hits, history));
                response.setMode(MODE_LLM_AUGMENTED);
                response.setGrounded(true);
                response.setModel(llm.name());
            } catch (BusinessException e) {
                // 模型调用失败不应让问答整体失败：降级为检索模式并如实说明
                log.warn("[KB] 生成失败，降级为检索模式：{}", e.getMessage());
                response.setLlmNotice("生成模型调用失败，已降级为检索模式：" + e.getMessage());
                fillRetrievalAnswer(response, hits);
            }
        } else {
            fillRetrievalAnswer(response, hits);
            response.setLlmNotice(llm == null ? defaultNotice() : llm.unavailableReason());
        }
        return response;
    }

    /**
     * 检索模式的回答：原文片段的整理，不是生成。
     *
     * <p>措辞上明确写出"未做改写"，避免用户误以为这是模型的总结——
     * 把片段拼接冒充成模型回答，是这个模块最容易犯的错。</p>
     */
    private void fillRetrievalAnswer(AskResponse response, List<ChunkHit> hits) {
        response.setMode(MODE_RETRIEVAL_ONLY);
        response.setGrounded(true);
        StringBuilder sb = new StringBuilder();
        sb.append("当前为检索模式：未启用生成模型，以下直接展示命中的原文片段（未做改写）。\n\n");
        int index = 1;
        for (ChunkHit hit : hits) {
            sb.append('[').append(index++).append("] ")
                    .append(hit.getDocumentTitle())
                    .append(" · ").append(hit.getSection()).append('\n')
                    .append(hit.getExcerpt()).append("\n\n");
        }
        response.setAnswer(sb.toString().trim());
    }

    /** 取第一个"真的可用"的模型实现；全部不可用时返回最后一个（noop）用于给出提示文案 */
    private LlmClient pickLlm() {
        if (llmClients == null || llmClients.isEmpty()) {
            return null;
        }
        for (LlmClient client : llmClients) {
            if (client.available()) {
                return client;
            }
        }
        return llmClients.get(llmClients.size() - 1);
    }

    private String defaultNotice() {
        return "未配置大模型服务，当前为检索模式（retrieval-only）。";
    }

    private void persistAssistant(Long conversationId, AskResponse response) {
        try {
            KbMessage message = conversationService.appendAssistantMessage(
                    conversationId, response.getAnswer(), response.getSources(), response.getMode());
            response.setConversationId(conversationId);
            response.setMessageId(message.getId());
        } catch (Exception e) {
            // 消息落库失败不影响把答案返回给用户，只记录
            log.warn("[KB] 助手消息落库失败：{}", e.getMessage());
        }
    }

    private void finish(AskResponse response, AuthUser user, long start) {
        response.setLatencyMs((int) (System.currentTimeMillis() - start));
        writeLog(response, user);
    }

    private static double topScoreOf(List<ChunkHit> hits) {
        return hits.isEmpty() ? 0D
                : hits.stream().mapToDouble(h -> h.getScore() == null ? 0D : h.getScore()).max().orElse(0D);
    }

    /**
     * 问答日志：只记问题与统计，不记答案正文。
     *
     * <p>答案正文在 kb_message 里有一份（用户自己的对话历史，仅本人可见）；
     * qa_log 是审计面，仍然不复制答案，避免绕过可见范围控制。</p>
     */
    private void writeLog(AskResponse response, AuthUser user) {
        try {
            KbQaLog log = new KbQaLog();
            log.setUserId(user == null ? null : user.getUserId());
            log.setQuestion(response.getQuestion());
            log.setHitCount(response.getSources() == null ? 0 : response.getSources().size());
            log.setTopScore(response.getTopScore() == null ? null
                    : BigDecimal.valueOf(response.getTopScore()).setScale(4, RoundingMode.HALF_UP));
            log.setMode(response.getMode());
            log.setLatencyMs(response.getLatencyMs());
            qaLogMapper.insert(log);
        } catch (Exception e) {
            // 日志写失败不能反过来让问答失败，只记录
            AgentOrchestrator.log.warn("[KB] 问答日志落库失败：{}", e.getMessage());
        }
    }
}
