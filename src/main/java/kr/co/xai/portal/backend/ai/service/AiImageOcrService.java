package kr.co.xai.portal.backend.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AiImageOcrService {

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // [보안 수정] 하드코딩된 키 제거 -> application.yml의 환경변수 주입 사용
    @Value("${openai.api.key}")
    private String apiKey;

    public AiImageOcrService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String extractRawText(MultipartFile image) {
        // 재시도 설정
        int maxRetries = 3;
        int retryDelayMs = 5000; // 5초 대기

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return callOpenAiApi(image);
            } catch (HttpClientErrorException.TooManyRequests e) {
                // 429 에러 발생 시 (Rate Limit)
                log.warn("⚠️ OpenAI Rate Limit 도달! {}초 후 재시도합니다... (시도: {}/{})", retryDelayMs / 1000, attempt,
                        maxRetries);

                if (attempt == maxRetries) {
                    throw new IllegalStateException("OpenAI API 호출 한도 초과 (잠시 후 다시 시도해주세요).", e);
                }

                try {
                    Thread.sleep(retryDelayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("대기 중 인터럽트 발생", ie);
                }
            } catch (Exception e) {
                // 그 외 에러는 즉시 중단
                log.error("OCR 원본 추출 중 오류 발생", e);
                throw new IllegalStateException("OCR 원본 추출 중 알 수 없는 오류 발생: " + e.getMessage());
            }
        }
        throw new IllegalStateException("OCR 추출 실패");
    }

    /**
     * 실제 OpenAI API 호출 메소드
     */
    private String callOpenAiApi(MultipartFile image) throws Exception {
        String base64Image = Base64.getEncoder().encodeToString(image.getBytes());

        // [프롬프트 수정] 사족 방지 및 텍스트만 출력 유도
        String prompt = "Extract all readable text from this image. Return ONLY the text, do not include any conversational filler.";

        Map<String, Object> message = Map.of(
                "role", "user",
                "content", List.of(
                        Map.of(
                                "type", "text",
                                "text", prompt),
                        Map.of(
                                "type", "image_url",
                                "image_url", Map.of(
                                        "url", "data:image/png;base64," + base64Image))));

        Map<String, Object> body = Map.of(
                "model", "gpt-4o-mini",
                "messages", List.of(message),
                "max_tokens", 1000);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // 환경변수에서 로드한 API Key 사용
        headers.setBearerAuth(apiKey);

        HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);

        ResponseEntity<String> response = restTemplate.exchange(
                OPENAI_URL,
                HttpMethod.POST,
                request,
                String.class);

        JsonNode root = objectMapper.readTree(response.getBody());
        return root.path("choices").get(0).path("message").path("content").asText();
    }

    /** OCR 정제 */
    public String cleanText(String rawText) {
        if (rawText == null)
            return "";
        return rawText
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{2,}", "\n")
                .trim();
    }
}