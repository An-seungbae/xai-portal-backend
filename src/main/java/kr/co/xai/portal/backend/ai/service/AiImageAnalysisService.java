package kr.co.xai.portal.backend.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.xai.portal.backend.ai.dto.A360AiAnalysisResponse;
import kr.co.xai.portal.backend.ai.dto.AiImageAnalysisResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@Slf4j
@Service
public class AiImageAnalysisService {

    private final AiImageOcrService ocrService;
    private final A360AiAnalysisService analysisService;
    private final AiImageStructuredService structuredService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${openai.api.key}")
    private String openAiKey;

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";

    public AiImageAnalysisService(
            AiImageOcrService ocrService,
            A360AiAnalysisService analysisService,
            AiImageStructuredService structuredService,
            RestTemplate restTemplate) {
        this.ocrService = ocrService;
        this.analysisService = analysisService;
        this.structuredService = structuredService;
        this.restTemplate = restTemplate;
    }

    /**
     * 1. 기존: OCR 텍스트 기반 분석 (유지)
     * 문서 이미지를 텍스트로 변환하여 구조화된 데이터를 추출합니다.
     */
    public AiImageAnalysisResponse analyze(MultipartFile image, String prompt, String language) {
        StringBuilder analysisSourceText = new StringBuilder();
        String ocrRawText = null;

        // 1️ 이미지 처리
        if (image != null && !image.isEmpty()) {
            ocrRawText = ocrService.extractRawText(image);
            String cleanOcr = ocrService.cleanText(ocrRawText);
            analysisSourceText.append(cleanOcr);
        }

        // 2 텍스트 프롬프트 병합
        if (prompt != null && !prompt.trim().isEmpty()) {
            if (analysisSourceText.length() > 0) {
                analysisSourceText.append("\n\n[사용자 질문/요청]\n");
            }
            analysisSourceText.append(prompt);
        }

        if (analysisSourceText.length() == 0) {
            throw new IllegalArgumentException("분석할 이미지 또는 텍스트를 입력해주세요.");
        }

        String finalText = analysisSourceText.toString();

        // 3 데이터 구조화 및 AI 분석
        Map<String, Object> structuredData = structuredService.extractStructuredData(finalText);
        A360AiAnalysisResponse ai = analysisService.analyzeFromOcrText(finalText, language);

        AiImageAnalysisResponse response = new AiImageAnalysisResponse();
        response.setOcrRawText(ocrRawText != null ? ocrRawText : "(이미지 없음 - 텍스트 기반 분석)");
        response.setOcrCleanText(finalText);
        response.setStructuredData(structuredData);
        response.setSummary(ai.getSummary());
        response.setCauseCandidates(ai.getCauseCandidates());
        response.setRecommendedActions(ai.getRecommendedActions());
        response.setBusinessMessage(ai.getBusinessMessage());

        return response;
    }

    /**
     * 2. [신규] Vision 기능: RPA 화면 에러 자동 진단
     * 이미지를 직접 보고 팝업, UI, 에러 메시지를 시각적으로 분석합니다.
     */
    public AiImageAnalysisResponse analyzeScreenError(MultipartFile image, String userPrompt, String language) {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("진단할 스크린샷 이미지가 필요합니다.");
        }

        try {
            // 1. 이미지 Base64 인코딩
            String base64Image = Base64.getEncoder().encodeToString(image.getBytes());

            // 2. 프롬프트 구성 (Vision 전용)
            String systemPrompt = "You are an expert RPA Troubleshooter. Analyze the screenshot of an Automation Anywhere (A360) bot execution failure.\n"
                    + "Identify error popups, red text, and UI context. Provide a technical diagnosis.";

            String userInstruction = String.format(
                    "Analyze this screenshot.\nUser Note: %s\n\n" +
                            "Return a JSON object with the following fields:\n" +
                            "- summary: Brief description of the visible screen and error.\n" +
                            "- causeCandidates: List of probable root causes (e.g., 'Selector not found', 'File path invalid').\n"
                            +
                            "- recommendedActions: Specific technical steps to fix it (e.g., 'Update XPath for [Login] button', 'Check network connection').\n"
                            +
                            "- businessMessage: A simple explanation for non-technical managers.\n" +
                            "Language: %s (Response MUST be in this language). JSON format only.",
                    (userPrompt == null ? "" : userPrompt),
                    ("en".equalsIgnoreCase(language) ? "English" : "Korean"));

            // 3. OpenAI Vision API 호출 Payload 구성
            Map<String, Object> message = Map.of(
                    "role", "user",
                    "content", List.of(
                            Map.of("type", "text", "text", userInstruction),
                            Map.of("type", "image_url", "image_url",
                                    Map.of("url", "data:image/jpeg;base64," + base64Image))));

            Map<String, Object> requestBody = Map.of(
                    "model", "gpt-4o",
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            message),
                    "max_tokens", 1500,
                    "response_format", Map.of("type", "json_object"));

            // 4. API 호출
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openAiKey);

            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);
            ResponseEntity<String> responseEntity = restTemplate.exchange(OPENAI_URL, HttpMethod.POST, entity,
                    String.class);

            // 5. 응답 파싱
            JsonNode root = objectMapper.readTree(responseEntity.getBody());
            String content = root.path("choices").get(0).path("message").path("content").asText();

            JsonNode resultNode = objectMapper.readTree(content);

            // 6. 결과 매핑
            AiImageAnalysisResponse response = new AiImageAnalysisResponse();
            response.setOcrRawText("(Vision Analysis - No OCR text extracted)");
            response.setOcrCleanText("Visual Diagnosis performed by GPT-4o Vision.");
            response.setSummary(resultNode.path("summary").asText());

            response.setCauseCandidates(objectMapper.convertValue(resultNode.path("causeCandidates"), ArrayList.class));
            response.setRecommendedActions(
                    objectMapper.convertValue(resultNode.path("recommendedActions"), ArrayList.class));

            response.setBusinessMessage(resultNode.path("businessMessage").asText());
            response.setStructuredData(Collections.emptyMap());

            return response;

        } catch (Exception e) {
            log.error("Vision Analysis Failed", e);
            throw new RuntimeException("화면 진단 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
}