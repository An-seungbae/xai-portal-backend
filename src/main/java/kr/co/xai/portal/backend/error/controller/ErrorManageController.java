package kr.co.xai.portal.backend.error.controller;

import kr.co.xai.portal.backend.error.service.ErrorManageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/errors")
@RequiredArgsConstructor
public class ErrorManageController {

    private final ErrorManageService errorManageService;

    /**
     * 오류 관리 목록 조회
     *
     * GET /api/errors?keyword=&page=1&size=20
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getErrors(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (page < 1)
            page = 1;
        if (size < 1)
            size = 20;

        return ResponseEntity.ok(
                errorManageService.getErrors(keyword, page, size));
    }

    /**
     * 🔹 A360 Execution 상세 조회 (신규)
     *
     * GET /api/errors/execution/{executionId}
     */
    @GetMapping("/execution/{executionId}")
    public ResponseEntity<Map<String, Object>> getExecutionDetail(
            @PathVariable String executionId) {

        return ResponseEntity.ok(
                errorManageService.getExecutionDetail(executionId));
    }
}
