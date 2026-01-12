package kr.co.xai.portal.backend.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.xai.portal.backend.ai.openai.OpenAiClient;
import kr.co.xai.portal.backend.ai.openai.OpenAiRequest;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AiImageStructuredService {

    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiImageStructuredService(OpenAiClient openAiClient) {
        this.openAiClient = openAiClient;
    }

    public Map<String, Object> extractStructuredData(String cleanOcrText) {

        try {
            // 1️ 프롬프트 (스키마 강제)
            String prompt = """
                    너는 OCR 텍스트를 분석하여 문서 유형을 분류하고 JSON을 생성한다.
                    [출력 형식]
                    { "documentType": "RECEIPT | ID_CARD | OTHER", "fields": { ... } }
                    OCR 텍스트:
                    """ + cleanOcrText;

            // 2️ [수정] messages 구성 (String, String 타입으로 변경)
            List<Map<String, String>> messages = new ArrayList<>();

            Map<String, String> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", "Return ONLY valid JSON. No explanation.");

            Map<String, String> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", prompt);

            messages.add(systemMessage);
            messages.add(userMessage);

            // 3️ OpenAI 요청 생성
            OpenAiRequest request = new OpenAiRequest();
            request.setModel("gpt-4o-mini");
            request.setMessages(messages);

            // [수정] max_tokens -> setMaxTokens
            request.setMaxTokens(900);

            // 4️ OpenAI 호출
            String rawJson = openAiClient.call(request);

            // 5️ [수정] 응답 파싱 (DTO 대신 JsonNode 사용으로 통일)
            JsonNode root = objectMapper.readTree(rawJson);

            // 안전한 경로 탐색
            JsonNode choices = root.path("choices");
            if (choices.isMissingNode() || choices.isEmpty()) {
                return Collections.emptyMap();
            }

            String contentJson = choices.get(0).path("message").path("content").asText();

            // 마크다운 제거
            if (contentJson.startsWith("```json")) {
                contentJson = contentJson.replace("```json", "").replace("```", "").trim();
            }

            // 6️ JSON → Map 변환
            return objectMapper.readValue(
                    contentJson,
                    new TypeReference<Map<String, Object>>() {
                    });

        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyMap();
        }
    }
}