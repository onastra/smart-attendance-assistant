package com.lianzhi.attendance.knowledge.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 无 Key 时的默认实现：必须声明不可用，且禁止被调用生成。
 */
class NoopLlmClientTest {

    private final NoopLlmClient client = new NoopLlmClient();

    @Test
    @DisplayName("Noop 始终不可用并给出降级说明")
    void unavailableWithReason() {
        assertFalse(client.available());
        assertNotNull(client.unavailableReason());
        assertTrue(client.unavailableReason().contains("检索"));
    }

    @Test
    @DisplayName("误调用 generate 应抛错，避免静默编造")
    void generateIsUnsupported() {
        assertThrows(UnsupportedOperationException.class,
                () -> client.generate("上班几点？", List.of()));
    }
}
