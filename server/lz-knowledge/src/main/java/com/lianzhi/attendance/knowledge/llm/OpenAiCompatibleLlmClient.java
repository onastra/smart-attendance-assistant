package com.lianzhi.attendance.knowledge.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lianzhi.attendance.common.exception.BusinessException;
import com.lianzhi.attendance.common.result.ResultCode;
import com.lianzhi.attendance.knowledge.dto.ChatTurn;
import com.lianzhi.attendance.knowledge.dto.ChunkHit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * OpenAI 兼容聊天接口实现。
 *
 * <p>只有 {@code lz.llm.enabled=true} 时才装配；装配后若缺 api-key / base-url，
 * 也只是标记不可用并记 WARN，<strong>不允许让应用起不来</strong>（需求 TC-F-08）。</p>
 *
 * <p>提示词里强制"只依据给定片段回答"，这是"不编造"（需求 IDX-F6）在工程上的第二道闸：
 * 第一道在检索层（低于阈值直接不答），第二道在这里（不允许补充自身知识）。</p>
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
@ConditionalOnProperty(prefix = "lz.llm", name = "enabled", havingValue = "true")
public class OpenAiCompatibleLlmClient implements LlmClient {

    private static final String SYSTEM_PROMPT =
            "你是企业知识库助手。只能使用用户提供的【资料片段】回答问题。"
                    + "片段与问题相关时就应当作答——即使用户的问法与片段措辞不同（如口语化表达对应制度条款），"
                    + "也要根据片段中的相关条款给出回答。"
                    + "只有当所有片段都与问题无关时，才回答“资料中没有足够依据，无法回答”，不允许补充你自己的知识，也不允许推测。"
                    + "回答时若引用了片段内容，用[1][2]标注对应的片段编号。"
                    + "若提供了对话历史，可结合历史理解问题中的指代（如“它”“上次说的”），但答案的事实依据仍只能来自本轮资料片段。";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${lz.llm.api-key:}")
    private String apiKey;

    @Value("${lz.llm.base-url:}")
    private String baseUrl;

    @Value("${lz.llm.model:}")
    private String model;

    private String unavailableReason;

    public OpenAiCompatibleLlmClient() {
        // 构造后再校验，见 #init
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        List<String> missing = new ArrayList<>();
        if (apiKey == null || apiKey.isBlank()) {
            missing.add("api-key");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            missing.add("base-url");
        }
        if (model == null || model.isBlank()) {
            missing.add("model");
        }
        if (!missing.isEmpty()) {
            unavailableReason = "已启用大模型（lz.llm.enabled=true）但缺少配置：" + String.join("、", missing)
                    + "。当前自动降级为检索模式。";
            log.warn("[LLM] {}", unavailableReason);
        } else {
            log.info("[LLM] 已启用生成增强：model={} base-url={}", model, baseUrl);
        }
    }

    @Override
    public boolean available() {
        return unavailableReason == null;
    }

    @Override
    public String name() {
        return model == null || model.isBlank() ? "openai-compatible" : model;
    }

    @Override
    public String unavailableReason() {
        return unavailableReason;
    }

    @Override
    public String generate(String question, List<ChunkHit> sources) {
        return generate(question, sources, List.of());
    }

    @Override
    public String generate(String question, List<ChunkHit> sources, List<ChatTurn> history) {
        if (!available()) {
            throw new BusinessException(ResultCode.DEPENDENCY_NOT_CONFIGURED, unavailableReason);
        }
        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", 0.2,
                "messages", buildMessages(question, sources, history)
        );
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(chatCompletionsUri()))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new BusinessException(ResultCode.DEPENDENCY_NOT_CONFIGURED,
                        "大模型返回 " + response.statusCode() + "：" + truncate(response.body()));
            }
            return objectMapper.readTree(response.body())
                    .path("choices").path(0).path("message").path("content")
                    .asText("");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.DEPENDENCY_NOT_CONFIGURED,
                    "调用大模型失败：" + e.getMessage());
        }
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    /**
     * 真流式：读取 SSE 数据行，逐段回调增量文本。
     *
     * <p>中途连接断开时把已收到的内容抛异常交给编排层——编排层会降级为
     * 检索模式整段返回，宁可降级也不把半截答案当成完整答案。</p>
     */
    @Override
    public void generateStream(String question, List<ChunkHit> sources,
                               List<ChatTurn> history, Consumer<String> deltaConsumer) {
        if (!available()) {
            throw new BusinessException(ResultCode.DEPENDENCY_NOT_CONFIGURED, unavailableReason);
        }
        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", 0.2,
                "stream", true,
                "messages", buildMessages(question, sources, history)
        );
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(chatCompletionsUri()))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<java.io.InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() / 100 != 2) {
                String errorBody = new String(response.body().readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8);
                throw new BusinessException(ResultCode.DEPENDENCY_NOT_CONFIGURED,
                        "大模型返回 " + response.statusCode() + "：" + truncate(errorBody));
            }
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(response.body(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring("data:".length()).trim();
                    if ("[DONE]".equals(data) || data.isEmpty()) {
                        continue;
                    }
                    String delta = objectMapper.readTree(data)
                            .path("choices").path(0).path("delta").path("content")
                            .asText("");
                    if (!delta.isEmpty()) {
                        deltaConsumer.accept(delta);
                    }
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.DEPENDENCY_NOT_CONFIGURED,
                    "流式调用大模型失败：" + e.getMessage());
        }
    }

    /** system +（可选）历史 + 本轮问题 */
    private List<Map<String, String>> buildMessages(String question, List<ChunkHit> sources,
                                                    List<ChatTurn> history) {
        StringBuilder context = new StringBuilder();
        int index = 1;
        for (ChunkHit hit : sources) {
            context.append('[').append(index++).append("] 来源：")
                    .append(hit.getDocumentTitle()).append(" · ").append(hit.getSection()).append('\n')
                    .append(hit.getContent()).append("\n\n");
        }
        String userContent = "【资料片段】\n" + context + "\n【问题】\n" + question;

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        if (history != null) {
            for (ChatTurn turn : history) {
                if (turn == null || turn.getContent() == null || turn.getContent().isBlank()) {
                    continue;
                }
                messages.add(Map.of("role", turn.getRole(), "content", turn.getContent()));
            }
        }
        messages.add(Map.of("role", "user", "content", userContent));
        return messages;
    }

    private String chatCompletionsUri() {
        return baseUrl.replaceAll("/+$", "") + "/chat/completions";
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 200 ? text.substring(0, 200) + "…" : text;
    }
}
