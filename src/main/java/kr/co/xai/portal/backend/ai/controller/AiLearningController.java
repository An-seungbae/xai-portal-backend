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
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/ai/learn")
@RequiredArgsConstructor
public class AiLearningController {

    private final AiLearningService aiLearningService;

    /**
     * 1. A360 자산 데이터 동기화 학습
     */
    @PostMapping("/a360")
    public ResponseEntity<Map<String, String>> syncA360Data() {
        String resultMessage = aiLearningService.learnA360Data();
        return ResponseEntity.ok(Map.of("message", resultMessage));
    }

    /**
     * 2. [신규] 매뉴얼 문서 개별 학습 API
     * MultipartFile과 추가 태그를 수신합니다.
     */
    @PostMapping("/manual")
    public ResponseEntity<Map<String, String>> learnManual(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "tag", required = false) String tag) {

        String result = aiLearningService.learnManualDocument(file, tag, "admin");
        return ResponseEntity.ok(Map.of("message", result));
    }

    /**
     * 3. 학습 이력 조회
     */
    @GetMapping("/history")
    public ResponseEntity<Page<AiLearningLog>> getLearningHistory(
            @PageableDefault(size = 10, sort = "learnedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(aiLearningService.getLearningHistory(pageable));
    }
}