package kr.co.xai.portal.backend.ai.controller;

import kr.co.xai.portal.backend.ai.dto.A360AiAnalysisRequest;
import kr.co.xai.portal.backend.ai.dto.A360AiAnalysisResponse;
import kr.co.xai.portal.backend.ai.dto.AiDailyBriefingResponse; //  추가
import kr.co.xai.portal.backend.ai.service.A360AiAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/a360")
public class A360AiAnalysisController {

    private final A360AiAnalysisService analysisService;

    public A360AiAnalysisController(A360AiAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    /**
     * 오류 원인 분석
     */
    @PostMapping("/error-analysis")
    public ResponseEntity<A360AiAnalysisResponse> analyze(@RequestBody A360AiAnalysisRequest request) {
        return ResponseEntity.ok(analysisService.analyze(request));
    }

    /**
     * 데일리 브리핑 생성
     * GET /api/ai/analysis/daily-briefing?lang=ko
     */
    @GetMapping("/daily-briefing")
    public ResponseEntity<AiDailyBriefingResponse> getDailyBriefing(
            @RequestParam(value = "lang", defaultValue = "ko") String lang) {
        return ResponseEntity.ok(analysisService.generateDailyBriefing(lang));
    }
}