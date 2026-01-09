package kr.co.xai.portal.backend.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiImageAnalysisResponse {

    /** OCR 원본 텍스트 */
    private String ocrRawText;

    /** OCR 정제 텍스트 */
    private String ocrCleanText;

    /** AI 요약 */
    private String summary;

    /** 원인 후보 */
    private List<String> causeCandidates;

    /** 권장 조치 */
    private List<String> recommendedActions;

    /** 비즈니스 메시지 */
    private String businessMessage;

    /** 🔹 OCR 구조화 데이터 (D단계-1) */
    private Map<String, Object> structuredData;
}