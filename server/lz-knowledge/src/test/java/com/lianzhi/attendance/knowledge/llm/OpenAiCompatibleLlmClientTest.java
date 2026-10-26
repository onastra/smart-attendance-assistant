package com.lianzhi.attendance.knowledge.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 不依赖外网的 URL / 契约断言：验证硅基流动等 OpenAI 兼容 Base URL 拼接正确。
 */
class OpenAiCompatibleLlmClientTest {

    @Test
    @DisplayName("硅基流动 Base URL 应拼出 /v1/chat/completions")
    void siliconFlowBaseUrlResolvesToChatCompletions() {
        String url = OpenAiCompatibleLlmClient.resolveChatCompletionsUrl(
                "https://api.siliconflow.cn/v1");
        assertEquals("https://api.siliconflow.cn/v1/chat/completions", url);
    }

    @Test
    @DisplayName("末尾多余斜杠应被去掉再拼接")
    void trailingSlashIsStripped() {
        String url = OpenAiCompatibleLlmClient.resolveChatCompletionsUrl(
                "https://api.siliconflow.cn/v1/");
        assertEquals("https://api.siliconflow.cn/v1/chat/completions", url);
    }

    @Test
    @DisplayName("空 Base URL 应拒绝")
    void blankBaseUrlRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> OpenAiCompatibleLlmClient.resolveChatCompletionsUrl("  "));
    }
}
