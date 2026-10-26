package com.lianzhi.attendance.knowledge.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lianzhi.attendance.common.exception.BusinessException;
import com.lianzhi.attendance.common.result.ResultCode;
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

/**
 * OpenAI 兼容聊天接口实现（{@code POST {base-url}/chat/completions}，Bearer Token）。
 *
 * <p>已与硅基流动（SiliconFlow）等 OpenAI 兼容厂商对齐：配置
 * {@code base-url=https://api.siliconflow.cn/v1}、{@code model=Qwen/Qwen2.5-7B-Instruct} 即可。
 * 只有 {@code lz.llm.enabled=true} 时才装配；装配后若缺 api-key / base-url / model，
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
                    + "如果片段中没有足够依据，必须回答“资料中没有足够依据，无法回答”，不允许补充你自己的知识，也不允许推测。"
                    + "回答时若引用了片段内容，用[1][2]标注对应的片段编号。";

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
        if (!available()) {
            throw new BusinessException(ResultCode.DEPENDENCY_NOT_CONFIGURED, unavailableReason);
        }
        StringBuilder context = new StringBuilder();
        int index = 1;
        for (ChunkHit hit : sources) {
            context.append('[').append(index++).append("] 来源：")
                    .append(hit.getDocumentTitle()).append(" · ").append(hit.getSection()).append('\n')
                    .append(hit.getContent()).append("\n\n");
        }
        String userContent = "【资料片段】\n" + context + "\n【问题】\n" + question;

        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", 0.2,
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userContent)
                )
        );

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(resolveChatCompletionsUrl(baseUrl)))
                    .timeout(Duration.ofSeconds(30))
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

    /**
     * 将配置中的 Base URL 解析为 chat completions 完整地址。
     *
     * <p>兼容硅基流动等 OpenAI 兼容厂商：配置 {@code https://api.siliconflow.cn/v1}
     * 时得到 {@code .../v1/chat/completions}；末尾多余斜杠会被去掉。</p>
     */
    static String resolveChatCompletionsUrl(String configuredBaseUrl) {
        if (configuredBaseUrl == null || configuredBaseUrl.isBlank()) {
            throw new IllegalArgumentException("base-url 不能为空");
        }
        return configuredBaseUrl.replaceAll("/+$", "") + "/chat/completions";
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 200 ? text.substring(0, 200) + "…" : text;
    }
}
