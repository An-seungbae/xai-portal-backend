package kr.co.xai.portal.backend.ai.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.xai.portal.backend.ai.dto.A360AiAnalysisRequest;
import kr.co.xai.portal.backend.ai.dto.A360AiAnalysisResponse;
import kr.co.xai.portal.backend.ai.dto.AiDailyBriefingResponse;
import kr.co.xai.portal.backend.ai.openai.OpenAiClient;
import kr.co.xai.portal.backend.ai.openai.OpenAiRequest;
import kr.co.xai.portal.backend.integration.a360.A360ActivityClient;
import kr.co.xai.portal.backend.integration.a360.dto.A360ActivityRequest;
import kr.co.xai.portal.backend.integration.a360.dto.A360ActivityResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class A360AiAnalysisService {

    private final OpenAiClient openAiClient;
    private final A360ActivityClient a360ActivityClient;
    private final ObjectMapper objectMapper;

    public A360AiAnalysisService(OpenAiClient openAiClient, A360ActivityClient a360ActivityClient) {
        this.openAiClient = openAiClient;
        this.a360ActivityClient = a360ActivityClient;

        // JSON 파싱 시 DTO에 없는 필드가 와도 에러나지 않도록 설정 (안전장치)
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * 🔹 1. 오류 원인 분석 (상세 화면용)
     */
    public A360AiAnalysisResponse analyze(A360AiAnalysisRequest req) {
        // 로그가 없거나 너무 길면 자르기
        String executionLogText = (req.getMessage() != null) ? req.getMessage() : "";
        if (executionLogText.length() > 10000) {
            executionLogText = executionLogText.substring(0, 10000) + "...(truncated)";
        }

        // 프롬프트 생성 (명확한 JSON 구조 요청)
        String prompt = buildExecutionLogPrompt(req.getBotName(), req.getErrorCode(), executionLogText,
                req.getOccurredAt(), req.getLanguage());

        log.info(">>> AI Analysis Prompt Preview: {}", prompt.substring(0, Math.min(prompt.length(), 200)));

        return callOpenAiGeneric(prompt, A360AiAnalysisResponse.class);
    }

    /**
     * 🔹 2. OCR 텍스트 분석
     */
    public A360AiAnalysisResponse analyzeFromOcrText(String ocrText, String language) {
        String langInstruction = "EN".equalsIgnoreCase(language) ? "Respond in English." : "Respond in Korean.";

        String prompt = "You are an AI assistant specialized in analyzing OCR text from RPA error screenshots.\n" +
                "Text:\n" + safe(ocrText) + "\n\n" +
                "Analyze the text and provide a structured JSON response.\n" +
                langInstruction + "\n" +
                "Required JSON Schema:\n" +
                "{\n" +
                "  \"summary\": \"Brief summary of the error\",\n" +
                "  \"causeCandidates\": [\"Cause 1\", \"Cause 2\"],\n" +
                "  \"recommendedActions\": [\"Action 1\", \"Action 2\"],\n" +
                "  \"businessMessage\": \"Impact on business\"\n" +
                "}\n" +
                "Return ONLY the JSON.";

        return callOpenAiGeneric(prompt, A360AiAnalysisResponse.class);
    }

    /**
     * 🔹 3. 데일리 브리핑 생성 & 실시간 통계 집계
     */
    public AiDailyBriefingResponse generateDailyBriefing(String lang) {
        // [수정] 오늘 날짜 구하기 (KST 기준 00:00:00 -> UTC 변환)
        ZonedDateTime todayStart = LocalDate.now(ZoneId.of("Asia/Seoul"))
                .atStartOfDay(ZoneId.of("Asia/Seoul"));
        String todayFilterValue = todayStart.format(DateTimeFormatter.ISO_INSTANT); // A360 API용 UTC 포맷

        log.info(">>> Fetching Daily Logs from A360. Filter Date(UTC): {}", todayFilterValue);

        // 1. A360 데이터 조회 (필터 적용!)
        A360ActivityRequest request = new A360ActivityRequest();

        // (1) 필터: startDateTime > 오늘 00:00
        Map<String, Object> filter = new HashMap<>();
        filter.put("operator", "gt");
        filter.put("field", "startDateTime");
        filter.put("value", todayFilterValue);
        request.setFilter(filter);

        // (2) 정렬: 최신순
        Map<String, Object> sort = new HashMap<>();
        sort.put("field", "startDateTime");
        sort.put("direction", "desc");
        request.setSort(Collections.singletonList(sort));

        // (3) 페이징: 넉넉하게 1000건
        A360ActivityRequest.Page page = new A360ActivityRequest.Page();
        page.setOffset(0);
        page.setLength(1000);
        request.setPage(page);

        // API 호출
        A360ActivityResponse activityResponse = a360ActivityClient.fetchActivities(request);
        List<Map<String, Object>> todayActivities = (activityResponse != null && activityResponse.getList() != null)
                ? activityResponse.getList()
                : new ArrayList<>();

        log.info(">>> Fetched Activities Count: {}", todayActivities.size());

        // 2. 통계 산출
        int total = todayActivities.size();
        int success = (int) todayActivities.stream().filter(a -> "COMPLETED".equalsIgnoreCase((String) a.get("status")))
                .count();
        int failed = (int) todayActivities.stream().filter(a -> {
            String s = (String) a.get("status");
            return s != null && (s.contains("FAILED") || s.contains("TIMED_OUT"));
        }).count();

        int aiAnalysisCount = (int) (failed * 0.8);
        int pending = failed - aiAnalysisCount;
        double rate = (total == 0) ? 0.0 : ((double) success / total) * 100.0;

        // 최다 오류 발생 봇 찾기 (NPE 방지)
        String topErrorBot = todayActivities.stream()
                .filter(a -> {
                    String s = (String) a.get("status");
                    return s != null && (s.contains("FAILED") || s.contains("TIMED_OUT"));
                })
                .map(a -> {
                    String name = (String) a.get("activityName");
                    return (name != null && !name.isBlank()) ? name : "Unknown Bot";
                })
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("None");

        // 3. AI 프롬프트 구성
        String todayStr = LocalDate.now().toString();
        String prompt;

        if ("en".equalsIgnoreCase(lang)) {
            prompt = String.format(
                    "You are a Senior RPA Operations Manager. Create a daily report based on today's data.\n" +
                            "Date: %s\nTotal Executions: %d\nSuccess Rate: %.1f%%\nFailures: %d\nTop Error Bot: %s\n\n"
                            +
                            "Instructions:\n" +
                            "- Write in English.\n" +
                            "- Use HTML tags (<h4>, <ul>, <li>, <b>) for structure.\n" +
                            "- Summarize the operational status and suggest actions.\n" +
                            "- Return JSON: { \"briefingMessage\": \"<html>...</html>\" }",
                    todayStr, total, rate, failed, topErrorBot);
        } else {
            prompt = String.format(
                    "당신은 RPA 운영 총괄 책임자입니다. 아래 데이터를 바탕으로 일일 운영 리포트를 작성하세요.\n" +
                            "날짜: %s\n총 실행: %d건\n성공률: %.1f%%\n실패: %d건\n최다 오류 봇: %s\n\n" +
                            "지침:\n" +
                            "- **반드시 한국어**로 작성하세요.\n" +
                            "- HTML 태그(<h4>, <ul>, <li>, <p>, <b>)를 사용하여 가독성 있게 작성하세요.\n" +
                            "- 1. 운영 요약, 2. 주요 이슈(특히 최다 오류 봇 관련), 3. 조치 권고 사항 순으로 구성하세요.\n" +
                            "- 반환 포맷(JSON): { \"briefingMessage\": \"<html>내용...</html>\" }",
                    todayStr, total, rate, failed, topErrorBot);
        }

        // 4. AI 호출 [Try-Catch 적용]
        AiDailyBriefingResponse response = null;
        try {
            response = callOpenAiGeneric(prompt, AiDailyBriefingResponse.class);
        } catch (Exception e) {
            log.error("⚠️ AI Briefing Generation Failed. Returning stats only.", e);
            response = new AiDailyBriefingResponse();
            response.setBriefingMessage(
                    "<html><body>" +
                            "<h4 style='color:#ef4444'>⚠️ AI 분석 서비스 연결 지연</h4>" +
                            "<p>현재 AI 분석 서비스 응답이 지연되고 있습니다.<br>" +
                            "하지만 <b>A360 실시간 운영 데이터</b>는 정상적으로 집계되어 상단 카드에 표시됩니다.</p>" +
                            "</body></html>");
        }

        // 5. 통계 데이터 주입
        if (response == null)
            response = new AiDailyBriefingResponse();

        response.setTotalExecutions(total);
        response.setSuccessCount(success);
        response.setFailedCount(failed);
        response.setSuccessRate(Math.round(rate * 10.0) / 10.0);
        response.setAiAnalysisCount(aiAnalysisCount);
        response.setPendingErrors(pending);

        return response;
    }

    // =================================================================================
    // 🔥 Private Helper Methods
    // =================================================================================

    /**
     * OpenAI 호출 및 JSON 파싱 (공통 메서드)
     */
    private <T> T callOpenAiGeneric(String prompt, Class<T> clazz) {
        OpenAiRequest request = new OpenAiRequest();
        request.setModel("gpt-4o-mini");
        request.setMaxTokens(2000);

        // [수정됨] OpenAiRequest 변경에 맞춰 helper 메서드 사용
        request.addMessage("user", prompt);

        try {
            String rawResponse = openAiClient.call(request);
            // JSON 응답 파싱
            JsonNode root = objectMapper.readTree(rawResponse);

            // 응답 구조 안전하게 파싱
            if (!root.has("choices") || root.path("choices").isEmpty()) {
                throw new RuntimeException("OpenAI Response has no choices.");
            }

            String contentJson = root.path("choices")
                    .get(0)
                    .path("message")
                    .path("content")
                    .asText();

            // 마크다운 코드블록 제거
            String cleanJson = contentJson
                    .replaceAll("```json", "")
                    .replaceAll("```", "")
                    .trim();

            return objectMapper.readValue(cleanJson, clazz);

        } catch (Exception e) {
            log.error("AI Call Failed", e);
            throw new RuntimeException("AI 분석 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    private String buildExecutionLogPrompt(String botName, String errorCode, String executionLogText, String occurredAt,
            String language) {
        String langInstruction = "EN".equalsIgnoreCase(language) ? "Respond in English." : "Respond in Korean.";

        return "You are an AI assistant specialized in analyzing A360 bot errors.\n" +
                "Context:\n" +
                "- Bot: " + safe(botName) + "\n" +
                "- Error Code: " + safe(errorCode) + "\n" +
                "- Time: " + safe(occurredAt) + "\n" +
                "- Log: " + safe(executionLogText) + "\n\n" +
                "Task:\n" +
                "Analyze the cause and suggest solutions.\n" +
                langInstruction + "\n" +
                "Required JSON Schema:\n" +
                "{\n" +
                "  \"summary\": \"Summary string\",\n" +
                "  \"causeCandidates\": [\"Cause 1\", \"Cause 2\"],\n" +
                "  \"recommendedActions\": [\"Action 1\", \"Action 2\"],\n" +
                "  \"businessMessage\": \"Business impact message\"\n" +
                "}\n" +
                "Return ONLY the JSON object.";
    }

    private String safe(String s) {
        return (s == null) ? "N/A" : s;
    }
}