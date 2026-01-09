package kr.co.xai.portal.backend.ai.controller;

import kr.co.xai.portal.backend.ai.entity.AiLearningLog;
import kr.co.xai.portal.backend.ai.service.AiLearningService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai/learn")
@RequiredArgsConstructor
public class AiLearningController {

    private final AiLearningService aiLearningService;

    @PostMapping("/a360")
    public ResponseEntity<Map<String, String>> syncA360Data() {
        String resultMessage = aiLearningService.learnA360Data();
        return ResponseEntity.ok(Map.of("message", resultMessage));
    }

    // [변경] 학습 이력 페이징 조회
    @GetMapping("/history")
    public ResponseEntity<Page<AiLearningLog>> getLearningHistory(
            @PageableDefault(size = 10, sort = "learnedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(aiLearningService.getLearningHistory(pageable));
    }
}