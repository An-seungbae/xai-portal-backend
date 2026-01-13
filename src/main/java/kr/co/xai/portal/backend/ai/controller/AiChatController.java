package kr.co.xai.portal.backend.ai.controller;

import kr.co.xai.portal.backend.ai.dto.AiChatRequest; // Import 추가
import kr.co.xai.portal.backend.ai.dto.AiChatResponse; // Import 추가
import kr.co.xai.portal.backend.ai.service.AiChatBotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatBotService aiChatBotService;

    /**
     * AI 채팅 API
     * Request Body 예시: { "message": "봇 상태 알려줘" }
     */
    @PostMapping("/chat")
    public ResponseEntity<AiChatResponse> chat(@RequestBody AiChatRequest request) {
        // 서비스 메서드가 DTO(AiChatRequest)를 받고 DTO(AiChatResponse)를 반환하므로
        // 컨트롤러도 이를 그대로 전달하는 구조가 가장 깔끔하고 확장성이 좋습니다.
        AiChatResponse response = aiChatBotService.chat(request);

        return ResponseEntity.ok(response);
    }
}