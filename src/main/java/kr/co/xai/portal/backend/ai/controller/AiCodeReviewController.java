package kr.co.xai.portal.backend.ai.controller;

import kr.co.xai.portal.backend.ai.service.AiCodeReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/ai/code")
@RequiredArgsConstructor
public class AiCodeReviewController {

    private final AiCodeReviewService codeReviewService;

    @PostMapping("/review")
    public ResponseEntity<Map<String, String>> reviewCode(@RequestParam("file") MultipartFile file) {
        String result = codeReviewService.reviewCodeFile(file);
        return ResponseEntity.ok(Map.of("report", result));
    }
}