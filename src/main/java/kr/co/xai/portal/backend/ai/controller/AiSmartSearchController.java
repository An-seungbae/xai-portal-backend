package kr.co.xai.portal.backend.ai.controller;

import kr.co.xai.portal.backend.ai.dto.AiSmartSearchResponse;
import kr.co.xai.portal.backend.ai.service.AiSmartSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai/search")
@RequiredArgsConstructor
public class AiSmartSearchController {

    private final AiSmartSearchService aiSmartSearchService;

    @PostMapping
    public ResponseEntity<AiSmartSearchResponse> search(@RequestBody Map<String, String> request) {
        String query = request.get("query");
        AiSmartSearchResponse response = aiSmartSearchService.searchGlobal(query);
        return ResponseEntity.ok(response);
    }
}