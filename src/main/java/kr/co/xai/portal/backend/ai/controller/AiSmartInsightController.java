package kr.co.xai.portal.backend.ai.controller;

import kr.co.xai.portal.backend.ai.dto.AiInsightDto;
import kr.co.xai.portal.backend.ai.service.AiSmartInsightService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ai/insight")
@RequiredArgsConstructor
public class AiSmartInsightController {

    private final AiSmartInsightService aiSmartInsightService;

    @GetMapping
    public ResponseEntity<List<AiInsightDto>> getAiInsights() {
        return ResponseEntity.ok(aiSmartInsightService.analyzeAndGenerateInsights());
    }
}