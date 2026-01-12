package kr.co.xai.portal.backend.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.xai.portal.backend.ai.dto.AiSmartSearchResponse;
import kr.co.xai.portal.backend.ai.openai.OpenAiClient;
import kr.co.xai.portal.backend.ai.openai.OpenAiRequest;
import kr.co.xai.portal.backend.integration.a360.A360ActivityClient;
import kr.co.xai.portal.backend.integration.a360.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiSmartSearchService {

    private final OpenAiClient openAiClient;
    private final A360ActivityClient a360Client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 메인 검색 메서드 (텍스트 + 파일)
     */
    public AiSmartSearchResponse search(String userQuery, MultipartFile file) {

        // 1. 이미지가 첨부된 경우 -> Vision 분석 모드로 직행
        if (file != null && !file.isEmpty()) {
            log.info("📸 Vision Analysis Request: query=[{}]", userQuery);
            String analysisResult = analyzeImageWithGpt(userQuery, file);

            return AiSmartSearchResponse.builder()
                    .intent("VISION")
                    .summary(analysisResult)
                    .data(new ArrayList<>())
                    .build();
        }

        // 2. 텍스트만 있는 경우 -> 의도 파악 후 분기 처리
        String intent = identifyIntent(userQuery);
        log.info("🔎 Smart Search Query: [{}], Intent: [{}]", userQuery, intent);

        Object searchResult = new ArrayList<>();
        String resultSummary = "";

        // 의도에 따른 분기 처리
        if ("SCHEDULE".equalsIgnoreCase(intent)) {
            A360ScheduleResponse res = a360Client.fetchSchedules();
            searchResult = res != null ? res.getList() : new ArrayList<>();
            resultSummary = "요청하신 예약된 스케줄 목록입니다.";

        } else if ("DEVICE".equalsIgnoreCase(intent)) {
            A360DeviceResponse res = a360Client.fetchDevices();
            searchResult = res != null ? res.getList() : new ArrayList<>();
            resultSummary = "등록된 디바이스 상태 목록입니다.";

        } else if ("HISTORY".equalsIgnoreCase(intent)) {
            // [수정] ActivityRequest를 사용하여 명시적으로 요청
            A360ActivityRequest req = new A360ActivityRequest();
            A360ActivityRequest.Page page = new A360ActivityRequest.Page();
            page.setOffset(0);
            page.setLength(100);
            req.setPage(page);

            A360ActivityResponse res = a360Client.fetchActivities(req);
            searchResult = res != null ? res.getList() : new ArrayList<>();
            resultSummary = "최근 봇 실행 이력입니다.";

        } else if ("RATE_LIMIT".equalsIgnoreCase(intent)) {
            intent = "System Alert";
            resultSummary = "현재 AI 사용량이 폭주하여 일시적으로 답변을 드릴 수 없습니다. 잠시 후 다시 시도해 주세요.";

        } else {
            // [GENERAL] RPA 외의 모든 질문
            intent = "GENERAL";
            resultSummary = generateGeneralAnswer(userQuery);
        }

        return AiSmartSearchResponse.builder()
                .intent(intent.toUpperCase())
                .summary(resultSummary)
                .data(searchResult)
                .build();
    }

    // ========================================================================
    // Private Helper Methods
    // ========================================================================

    /**
     * GPT-4o Vision API 호출 (이미지 분석)
     */
    private String analyzeImageWithGpt(String query, MultipartFile file) {
        // [임시 처리] 멀티파트 파일 처리는 별도 로직이 필요하므로 현재는 안내 메시지 반환
        // 실제 구현 시에는 이미지를 Base64로 인코딩하여 OpenAiRequest의 messages에 content(type:
        // image_url)로 추가해야 함
        return "[Vision Analysis Result]\n" +
                "이미지 분석 요청이 접수되었습니다.\n" +
                "현재 모드에서는 텍스트 기반 검색만 지원됩니다.\n" +
                "(Vision 기능 활성화를 위해 AiImageAnalysisService를 이용해주세요)";
    }

    /**
     * 의도 분류 (RPA 3가지 + GENERAL)
     */
    private String identifyIntent(String query) {
        String prompt = "Classify user's intent.\n" +
                "1. 'SCHEDULE': Future, reservation, plan\n" +
                "2. 'DEVICE': Agent, PC, connection, status\n" +
                "3. 'HISTORY': Past logs, success/fail, error\n" +
                "4. 'GENERAL': Everything else\n\n" +
                "User Query: " + query + "\n" +
                "Respond ONLY with one word.";

        try {
            OpenAiRequest req = new OpenAiRequest();
            req.setModel("gpt-4o-mini");
            req.setMaxTokens(50);

            // [수정] Helper 메서드 사용
            req.addMessage("user", prompt);

            String raw = openAiClient.call(req);
            JsonNode root = objectMapper.readTree(raw);

            if (!root.has("choices") || root.path("choices").isEmpty()) {
                return "GENERAL";
            }

            String cleanIntent = root.path("choices").get(0).path("message").path("content").asText()
                    .trim().toUpperCase().replace(".", "").replace("'", "");

            if (cleanIntent.contains("SCHEDULE"))
                return "SCHEDULE";
            if (cleanIntent.contains("DEVICE"))
                return "DEVICE";
            if (cleanIntent.contains("HISTORY"))
                return "HISTORY";

            return "GENERAL";

        } catch (HttpClientErrorException.TooManyRequests e) {
            log.error("Intent Detection Rate Limit Exceeded", e);
            return "RATE_LIMIT";
        } catch (Exception e) {
            log.error("Intent Error", e);
            return "GENERAL";
        }
    }

    /**
     * 일반 질문 답변 생성
     */
    private String generateGeneralAnswer(String query) {
        String today = java.time.LocalDate.now().toString();

        String prompt = "You are a AI Assistant for 'XAI RPA Portal'.\n" +
                "Date: " + today + "\nQuery: " + query + "\n" +
                "Answer politely in Korean.";

        try {
            OpenAiRequest req = new OpenAiRequest();
            req.setModel("gpt-4o-mini");
            req.setMaxTokens(1000);

            // [수정] Helper 메서드 사용
            req.addMessage("user", prompt);

            String raw = openAiClient.call(req);
            JsonNode root = objectMapper.readTree(raw);

            if (!root.has("choices") || root.path("choices").isEmpty()) {
                return "답변을 생성할 수 없습니다.";
            }

            return root.path("choices").get(0).path("message").path("content").asText();

        } catch (HttpClientErrorException.TooManyRequests e) {
            log.error("General Answer Rate Limit Exceeded", e);
            return "⚠️ 죄송합니다. 현재 AI 사용량이 폭주하여 답변을 생성할 수 없습니다. (Rate Limit Exceeded) \n잠시 후 다시 질문해 주세요.";
        } catch (Exception e) {
            log.error("General Answer Error", e);
            return "죄송합니다. 답변을 생성하는 도중 문제가 발생했습니다.";
        }
    }
}