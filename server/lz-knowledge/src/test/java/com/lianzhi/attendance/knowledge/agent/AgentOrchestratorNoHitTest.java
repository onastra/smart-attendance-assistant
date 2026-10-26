package com.lianzhi.attendance.knowledge.agent;

import com.lianzhi.attendance.framework.auth.AuthUser;
import com.lianzhi.attendance.knowledge.dto.AskResponse;
import com.lianzhi.attendance.knowledge.dto.ChunkHit;
import com.lianzhi.attendance.knowledge.llm.LlmClient;
import com.lianzhi.attendance.knowledge.mapper.KbQaLogMapper;
import com.lianzhi.attendance.knowledge.retrieve.Retriever;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * 无检索命中时，即使有可用模型也不得调用生成（不编造）。
 */
@ExtendWith(MockitoExtension.class)
class AgentOrchestratorNoHitTest {

    @Mock
    private Retriever retriever;

    @Mock
    private KbQaLogMapper qaLogMapper;

    @Test
    @DisplayName("检索无命中时不调用 LlmClient，mode 仍为 retrieval-only")
    void noHitDoesNotCallLlmEvenWhenAvailable() {
        when(retriever.retrieve(anyString(), any(AuthUser.class), isNull(), anyInt()))
                .thenReturn(List.of());

        LlmClient availableButMustNotRun = new LlmClient() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public String name() {
                return "fake-must-not-run";
            }

            @Override
            public String unavailableReason() {
                return null;
            }

            @Override
            public String generate(String question, List<ChunkHit> sources) {
                fail("无命中时不应调用 generate");
                return "";
            }
        };

        AgentOrchestrator orchestrator = new AgentOrchestrator(
                retriever, List.of(availableButMustNotRun), qaLogMapper);

        AuthUser user = new AuthUser();
        user.setUserId(1L);
        AskResponse response = orchestrator.ask("完全不存在的制度问题", user, null, 5);

        assertFalse(response.isGrounded());
        assertEquals(AgentOrchestrator.MODE_RETRIEVAL_ONLY, response.getMode());
        assertEquals("NO_HIT", response.getReason());
        assertTrue(response.getAnswer().contains("未找到足够依据"));
    }
}
