package kr.co.xai.portal.backend.ai.controller;

import kr.co.xai.portal.backend.ai.dto.AiChatRequest;
import kr.co.xai.portal.backend.ai.dto.AiChatResponse;
import kr.co.xai.portal.backend.ai.service.AiAgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/agent")
@RequiredArgsConstructor
public class AiAgentController {

    private final AiAgentService aiAgentService;

    /**
     * AI Agent 채팅 (도구 사용 가능)
     * POST /api/ai/agent/chat
     */
    @PostMapping("/chat")
    public ResponseEntity<AiChatResponse> chat(@RequestBody AiChatRequest request) {
        String answer = aiAgentService.runAgent(request.getMessage());

        return ResponseEntity.ok(
                AiChatResponse.builder()
                        .answer(answer) // [수정] 필드명 'answer'에 맞게 수정
                        .intent("AGENT_PROCESSING") // 필요 시 인텐트 정보 추가 (선택사항)
                        .build());
    }
}