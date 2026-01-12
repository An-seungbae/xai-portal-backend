package kr.co.xai.portal.backend.ai.controller;

import kr.co.xai.portal.backend.ai.dto.A360AiAnalysisRequest;
import kr.co.xai.portal.backend.ai.dto.A360AiAnalysisResponse;
import kr.co.xai.portal.backend.ai.dto.AiDailyBriefingResponse;
import kr.co.xai.portal.backend.ai.service.A360AiAnalysisService;
import lombok.extern.slf4j.Slf4j; // 로깅을 위해 추가
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/ai/a360")
// CORS 허용: 프론트엔드 포트(보통 5173, 8080)에서의 접근을 허용합니다.
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class A360AiAnalysisController {

    private final A360AiAnalysisService analysisService;

    public A360AiAnalysisController(A360AiAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    /**
     * 오류 원인 분석 (AI Analysis)
     * Frontend: /api/ai/a360/error-analysis
     */
    @PostMapping("/error-analysis")
    public ResponseEntity<A360AiAnalysisResponse> analyze(@RequestBody A360AiAnalysisRequest request) {
        log.info(">>> AI Analysis Requested for Bot: {}", request.getBotName());
        log.info(">>> Error Code: {}, Message Length: {}", request.getErrorCode(),
                (request.getMessage() != null ? request.getMessage().length() : 0));

        try {
            A360AiAnalysisResponse response = analysisService.analyze(request);
            log.info(">>> AI Analysis Completed Successfully.");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error(">>> AI Analysis Failed: ", e);
            throw e; // Global Exception Handler가 처리하거나, 500 에러 반환
        }
    }

    /**
     * 데일리 브리핑 생성
     * GET /api/ai/a360/daily-briefing?lang=ko
     */
    @GetMapping("/daily-briefing")
    public ResponseEntity<AiDailyBriefingResponse> getDailyBriefing(
            @RequestParam(value = "lang", defaultValue = "ko") String lang) {

        log.info(">>> Daily Briefing Requested. Lang: {}", lang);
        return ResponseEntity.ok(analysisService.generateDailyBriefing(lang));
    }
}