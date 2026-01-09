package kr.co.xai.portal.backend.ai.controller;

import kr.co.xai.portal.backend.ai.dto.BotRiskDto;
import kr.co.xai.portal.backend.ai.service.AiPredictiveMaintenanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ai/predict")
@RequiredArgsConstructor
public class AiPredictiveMaintenanceController {

    private final AiPredictiveMaintenanceService predictiveService;

    @GetMapping("/risks")
    public ResponseEntity<List<BotRiskDto>> getRiskAnalysis() {
        return ResponseEntity.ok(predictiveService.analyzeBotRisks());
    }
}