package kr.co.xai.portal.backend.ai.controller;

import kr.co.xai.portal.backend.ai.service.AiChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatService aiChatService;

    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(@RequestBody Map<String, String> request) {
        String userMessage = request.get("message");
        String answer = aiChatService.chat(userMessage);

        return ResponseEntity.ok(Map.of("answer", answer));
    }
}