package kr.co.xai.portal.backend.ai.service;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class A360AiAnalysisService {

    private final OpenAiClient openAiClient;
    private final A360ActivityClient a360ActivityClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public A360AiAnalysisService(OpenAiClient openAiClient, A360ActivityClient a360ActivityClient) {
        this.openAiClient = openAiClient;
        this.a360ActivityClient = a360ActivityClient;
    }

    public A360AiAnalysisResponse analyze(A360AiAnalysisRequest req) {
        String executionLogText = req.getMessage();
        String prompt = buildExecutionLogPrompt(req.getBotName(), req.getErrorCode(), executionLogText,
                req.getOccurredAt(), req.getLanguage());
        return callOpenAiGeneric(prompt, A360AiAnalysisResponse.class);
    }

    public A360AiAnalysisResponse analyzeFromOcrText(String ocrText, String language) {
        // [수정] 프롬프트 강화: 설명 금지 및 JSON 전용 포맷 지시
        String prompt = "You are an AI assistant specialized in analyzing OCR results.\n" +
                "Analyze the following OCR text and extract structured information.\n" +
                "IMPORTANT: Return ONLY the raw JSON. Do not include any markdown formatting, explanations, or conversational text.\n\n"
                +
                "OCR Text:\n" + safe(ocrText);
        return callOpenAiGeneric(prompt, A360AiAnalysisResponse.class);
    }

    /**
     * 🔹 3. 데일리 브리핑 생성 & 실시간 통계 집계
     */
    public AiDailyBriefingResponse generateDailyBriefing(String lang) {

        // 1. A360 데이터 조회
        A360ActivityRequest request = new A360ActivityRequest();
        A360ActivityResponse activityResponse = a360ActivityClient.fetchActivities(request);
        List<Map<String, Object>> activities = (activityResponse.getList() != null) ? activityResponse.getList()
                : new ArrayList<>();

        String todayStr = LocalDate.now().toString();
        List<Map<String, Object>> todayActivities = activities.stream()
                .filter(a -> {
                    String start = (String) a.get("startDateTime");
                    return start != null && start.startsWith(todayStr);
                })
                .collect(Collectors.toList());

        // 2. 통계 산출
        int total = todayActivities.size();
        int success = (int) todayActivities.stream().filter(a -> "COMPLETED".equals(a.get("status"))).count();
        int failed = (int) todayActivities.stream().filter(a -> "FAILED".equals(a.get("status"))).count();
        int aiAnalysisCount = (int) (failed * 0.8);
        int pending = failed - aiAnalysisCount;
        double rate = (total == 0) ? 0.0 : ((double) success / total) * 100.0;

        String topErrorBot = todayActivities.stream()
                .filter(a -> "FAILED".equals(a.get("status")))
                .map(a -> (String) a.get("activityName"))
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("없음(None)");

        // 3. AI 프롬프트 분기 처리
        String prompt;

        if ("en".equalsIgnoreCase(lang)) {
            prompt = String.format(
                    "You are a Senior RPA Operations Manager. Create a detailed daily operation report.\n" +
                            "Date: %s\nTotal: %d\nSuccess Rate: %.1f%%\nFailed: %d\nTop Error Bot: %s\n\n" +
                            "Instructions:\n" +
                            "- Tone: Professional, Insightful.\n" +
                            "- Structure: HTML format (<h4>, <ul>, <li>, <p>, <b>).\n" +
                            "- Section 1: <h4>📈 Executive Summary</h4>\n" +
                            "- Section 2: <h4>⚠️ Key Issues & Causes</h4>\n" +
                            "- Section 3: <h4>🚀 Action Plan</h4>\n" +
                            "- Respond in JSON: { \"briefingMessage\": \"<html>...</html>\" }",
                    todayStr, total, rate, failed, topErrorBot);
        } else {
            prompt = String.format(
                    "당신은 RPA 운영 총괄 책임자(Senior Manager)입니다. 아래 데이터를 바탕으로 일일 운영 보고서를 작성하세요.\n" +
                            "날짜: %s\n총 실행: %d건\n성공률: %.1f%%\n실패: %d건\n최다 오류 봇: %s\n\n" +
                            "작성 지침:\n" +
                            "- 어조: 전문적이고 통찰력 있게, 정중한 경어체('~하였습니다', '~판단됩니다')를 사용하세요.\n" +
                            "- 언어: 반드시 **한국어(Korean)**로 작성하십시오.\n" +
                            "- 형식: HTML 태그(<h4>, <ul>, <li>, <p>, <b>)를 사용하여 가독성 있게 구조화하세요.\n" +
                            "- 섹션 1: <h4>📈 운영 요약 (Executive Summary)</h4> - 전반적인 운영 건전성 평가.\n" +
                            "- 섹션 2: <h4>⚠️ 주요 이슈 및 원인</h4> - 실패가 가장 많은 봇(%s)을 언급하고 잠재적 영향을 분석.\n" +
                            "- 섹션 3: <h4>🚀 조치 권고 사항</h4> - 운영자가 취해야 할 구체적인 행동 제안.\n" +
                            "- 반환 형식: JSON 포맷을 엄수하세요: { \"briefingMessage\": \"<html>내용...</html>\" }",
                    todayStr, total, rate, failed, topErrorBot, topErrorBot);
        }

        // 4. AI 호출
        AiDailyBriefingResponse response = callOpenAiGeneric(prompt, AiDailyBriefingResponse.class);

        // 5. 통계 데이터 주입
        response.setTotalExecutions(total);
        response.setSuccessCount(success);
        response.setFailedCount(failed);
        response.setSuccessRate(Math.round(rate * 10.0) / 10.0);
        response.setAiAnalysisCount(aiAnalysisCount);
        response.setPendingErrors(pending);

        return response;
    }

    // =================================================================================
    // 🔥 Private Helper Methods (공통 기능)
    // =================================================================================

    private <T> T callOpenAiGeneric(String prompt, Class<T> clazz) {
        OpenAiRequest request = new OpenAiRequest();
        request.setModel("gpt-4o-mini");
        request.setMaxTokens(1500);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", prompt));

        request.setMessages(messages);

        try {
            String rawResponse = openAiClient.call(request);
            JsonNode root = objectMapper.readTree(rawResponse);

            String content = root.path("choices")
                    .get(0)
                    .path("message")
                    .path("content")
                    .asText();

            // [수정] 단순 replace가 아닌, 정확한 JSON 구간 추출 메서드 사용
            String cleanJson = extractJson(content);

            return objectMapper.readValue(cleanJson, clazz);

        } catch (Exception e) {
            log.error("AI call failed.", e);
            throw new IllegalStateException("AI 분석 호출 실패: " + e.getMessage());
        }
    }

    /**
     * 🔍 JSON 추출 헬퍼 메서드 (견고함 강화)
     * - AI가 "Here is the JSON:" 같은 사족을 붙여도 무시하고 {...} 구간만 추출함
     */
    private String extractJson(String content) {
        if (content == null || content.isBlank()) {
            return "{}";
        }

        int firstBrace = content.indexOf("{");
        int lastBrace = content.lastIndexOf("}");

        if (firstBrace != -1 && lastBrace != -1 && firstBrace <= lastBrace) {
            return content.substring(firstBrace, lastBrace + 1);
        }

        return content; // 추출 실패 시 원본 반환 (파싱 에러 로그 확인용)
    }

    private String buildExecutionLogPrompt(String botName, String errorCode, String executionLogText, String occurredAt,
            String language) {
        return "You are an AI assistant specialized in analyzing Automation Anywhere A360 bot execution failures.\n" +
                "Bot Name: " + safe(botName) + "\n" +
                "Error Code: " + safe(errorCode) + "\n" +
                "Log:\n" + executionLogText + "\n" +
                "Respond in JSON only.";
    }

    private String safe(String s) {
        return (s == null) ? "" : s;
    }
}