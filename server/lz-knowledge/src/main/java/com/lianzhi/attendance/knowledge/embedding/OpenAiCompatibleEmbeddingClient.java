package com.lianzhi.attendance.knowledge.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lianzhi.attendance.common.exception.BusinessException;
import com.lianzhi.attendance.common.result.ResultCode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * OpenAI 兼容 /embeddings 接口实现（硅基流动等）。
 *
 * <p>装配条件与 {@code OpenAiCompatibleLlmClient} 对齐：{@code lz.llm.embedding.enabled=true}
 * 才装配；复用 {@code lz.llm.api-key / base-url}，模型独立配置
 * （{@code lz.llm.embedding.model}，默认 BAAI/bge-large-zh-v1.5）。</p>
 *
 * <p>缺配置只记 WARN 并降级为"纯全文检索"，不让应用起不来——语义检索是增强项，
 * 不是问答主链路的必要依赖。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "lz.llm.embedding", name = "enabled", havingValue = "true")
public class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {

    /** 单次请求的最大文本数：防止超长请求被网关拒绝，也控制单次失败的影响面 */
    private static final int BATCH_SIZE = 32;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${lz.llm.api-key:}")
    private String apiKey;

    @Value("${lz.llm.base-url:}")
    private String baseUrl;

    @Value("${lz.llm.embedding.model:BAAI/bge-large-zh-v1.5}")
    private String model;

    private String unavailableReason;
    private volatile int dimension;

    @PostConstruct
    public void init() {
        List<String> missing = new ArrayList<>();
        if (apiKey == null || apiKey.isBlank()) {
            missing.add("api-key（复用 lz.llm.api-key）");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            missing.add("base-url（复用 lz.llm.base-url）");
        }
        if (!missing.isEmpty()) {
            unavailableReason = "已启用语义检索（lz.llm.embedding.enabled=true）但缺少配置："
                    + String.join("、", missing) + "。当前退化为纯全文检索。";
            log.warn("[Embedding] {}", unavailableReason);
        } else {
            log.info("[Embedding] 已启用语义检索：model={} base-url={}", model, baseUrl);
        }
    }

    @Override
    public boolean available() {
        return unavailableReason == null;
    }

    @Override
    public String name() {
        return model;
    }

    @Override
    public String unavailableReason() {
        return unavailableReason;
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (!available()) {
            throw new BusinessException(ResultCode.DEPENDENCY_NOT_CONFIGURED, unavailableReason);
        }
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        List<float[]> result = new ArrayList<>(texts.size());
        // 分批调用：一批失败只影响这一批的调用方，上层按"该批无向量"降级处理
        for (int from = 0; from < texts.size(); from += BATCH_SIZE) {
            List<String> batch = texts.subList(from, Math.min(from + BATCH_SIZE, texts.size()));
            result.addAll(embedBatch(batch));
        }
        return result;
    }

    private List<float[]> embedBatch(List<String> batch) {
        Map<String, Object> body = Map.of(
                "model", model,
                "input", batch,
                "encoding_format", "float"
        );
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl.replaceAll("/+$", "") + "/embeddings"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new BusinessException(ResultCode.DEPENDENCY_NOT_CONFIGURED,
                        "向量服务返回 " + response.statusCode() + "：" + truncate(response.body()));
            }
            JsonNode data = objectMapper.readTree(response.body()).path("data");
            List<float[]> vectors = new ArrayList<>(batch.size());
            for (JsonNode item : data) {
                JsonNode arr = item.path("embedding");
                float[] vector = new float[arr.size()];
                for (int i = 0; i < arr.size(); i++) {
                    vector[i] = (float) arr.get(i).asDouble();
                }
                if (dimension == 0 && vector.length > 0) {
                    dimension = vector.length;
                }
                vectors.add(vector);
            }
            if (vectors.size() != batch.size()) {
                throw new BusinessException(ResultCode.DEPENDENCY_NOT_CONFIGURED,
                        "向量服务返回数量与请求不一致：期望 " + batch.size() + " 实际 " + vectors.size());
            }
            return vectors;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.DEPENDENCY_NOT_CONFIGURED,
                    "调用向量服务失败：" + e.getMessage());
        }
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 200 ? text.substring(0, 200) + "…" : text;
    }
}
