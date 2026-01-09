package kr.co.xai.portal.backend.ai.controller;

import kr.co.xai.portal.backend.ai.dto.AiSmartSearchResponse;
import kr.co.xai.portal.backend.ai.service.AiSmartSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ai/search")
@RequiredArgsConstructor
public class AiSmartSearchController {

    private final AiSmartSearchService searchService;

    // JSON Body 대신 Multipart/form-data로 변경
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AiSmartSearchResponse> search(
            @RequestParam("query") String query,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        return ResponseEntity.ok(searchService.search(query, file));
    }
}