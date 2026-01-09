package kr.co.xai.portal.backend.ai.controller;

import kr.co.xai.portal.backend.ai.dto.AiHistoryListDto;
import kr.co.xai.portal.backend.ai.dto.AiImageAnalysisResponse;
import kr.co.xai.portal.backend.ai.dto.AiImageSaveRequest;
import kr.co.xai.portal.backend.ai.dto.AiImageSaveResponse;
import kr.co.xai.portal.backend.ai.entity.AiDocument;
import kr.co.xai.portal.backend.ai.service.AiDocumentStorageService;
import kr.co.xai.portal.backend.ai.service.AiImageAnalysisService;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/ai/image")

public class AiImageAnalysisController {

    private final AiImageAnalysisService imageAnalysisService;
    private final AiDocumentStorageService storageService;

    public AiImageAnalysisController(
            AiImageAnalysisService imageAnalysisService,
            AiDocumentStorageService storageService) {
        this.imageAnalysisService = imageAnalysisService;
        this.storageService = storageService;
    }

    /**
     * [변경] 스마트 검색 (이미지 or 텍스트 or 둘 다)
     * 이미지가 없으면 텍스트만 분석하고, 둘 다 있으면 통합 분석합니다.
     */
    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AiImageAnalysisResponse analyze(
            @RequestPart(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "prompt", required = false) String prompt,
            @RequestParam(defaultValue = "KO") String language) {
        return imageAnalysisService.analyze(image, prompt, language);
    }

    @PostMapping(value = "/save", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AiImageSaveResponse saveResult(
            @RequestPart("request") AiImageSaveRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        return storageService.save(request, file);
    }

    @GetMapping("/history/{id}/image")
    public ResponseEntity<Resource> downloadImage(@PathVariable Long id) {
        Resource resource = storageService.loadFileAsResource(id);
        String fileName = resource.getFilename();

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                .body(resource);
    }

    // [변경] Pageable 적용
    @GetMapping("/history")
    public ResponseEntity<Page<AiHistoryListDto>> getHistory(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(storageService.getAllHistory(pageable));
    }

    @GetMapping("/history/{id}")
    public AiDocument getHistoryDetail(@PathVariable Long id) {
        return storageService.getDocumentDetail(id);
    }
}