package kr.co.xai.portal.backend.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.xai.portal.backend.ai.entity.AiLearningLog;
import kr.co.xai.portal.backend.ai.openai.OpenAiClient;
import kr.co.xai.portal.backend.ai.openai.OpenAiRequest;
import kr.co.xai.portal.backend.ai.repository.AiLearningLogRepository;
import kr.co.xai.portal.backend.integration.a360.A360ActivityClient;
import kr.co.xai.portal.backend.integration.a360.dto.A360ActivityRequest;
import kr.co.xai.portal.backend.integration.a360.dto.A360ActivityResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value; // Import 추가
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    private final OpenAiClient openAiClient;
    private final A360ActivityClient a360ActivityClient;
    private final AiLearningLogRepository learningLogRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // [추가] 설정 파일에서 max-tokens 값 가져오기 (기본값 1000)
    @Value("${openai.api.max-tokens:1000}")
    private int defaultMaxTokens;

    /**
     * 사용자의 메시지를 받아 AI 답변을 생성합니다.
     */
    public String chat(String userMessage) {
        try {
            // 1. 의도 파악
            String intent = identifyIntent(userMessage);
            log.info(">> User Intent: {}", intent);

            if ("ERROR_LOG".equals(intent)) {
                return generateLiveErrorAnswer(userMessage);
            } else {
                return generateGeneralAnswerWithRAG(userMessage);
            }

        } catch (HttpClientErrorException e) {
            // OpenAI 비용 부족(429) 시 Mock 응답
            if (e.getStatusCode().value() == 429) {
                log.warn("OpenAI Quota Exceeded. Returning Mock Response.");
                return getMockChatResponse(userMessage);
            }
            log.error("OpenAI API Error: {}", e.getResponseBodyAsString()); // 에러 바디 상세 로그
            return "죄송합니다. AI 서버 연결 중 오류가 발생했습니다.";
        } catch (Exception e) {
            log.error("Chat Service Error", e);
            return "시스템 내부 오류가 발생했습니다. 관리자에게 문의하세요.";
        }
    }

    // --- Private Methods ---

    private String identifyIntent(String message) {
        if (message.contains("오류") || message.contains("에러") || message.contains("실패") || message.contains("멈췄")
                || message.contains("안 돌아")) {
            return "ERROR_LOG";
        }
        return "GENERAL";
    }

    private String generateLiveErrorAnswer(String userMessage) {
        try {
            A360ActivityRequest req = new A360ActivityRequest();

            String today = ZonedDateTime.now().minusDays(1).format(DateTimeFormatter.ISO_INSTANT);
            Map<String, Object> filter = new HashMap<>();
            filter.put("operator", "gt");
            filter.put("field", "startDateTime");
            filter.put("value", today);
            req.setFilter(filter);

            A360ActivityResponse res = a360ActivityClient.fetchActivities(req);

            List<Map<String, Object>> failedLogs = res.getList().stream()
                    .filter(item -> {
                        String status = (String) item.get("status");
                        return status != null && (status.contains("FAIL") || status.contains("UNKNOWN"));
                    })
                    .limit(3)
                    .collect(Collectors.toList());

            if (failedLogs.isEmpty()) {
                return "최근 24시간 내에 발견된 **오류 로그가 없습니다.** 시스템이 안정적으로 운영되고 있습니다. 👍";
            }

            StringBuilder logContext = new StringBuilder();
            for (Map<String, Object> log : failedLogs) {
                logContext.append(String.format("- 봇: %s, 상태: %s, 시간: %s\n",
                        log.get("activityName"), log.get("status"), log.get("startDateTime")));
            }

            String prompt = "User Question: " + userMessage + "\n\n" +
                    "[Real-time Error Logs]\n" + logContext + "\n\n" +
                    "Analyze these logs and explain what happened in Korean. Be concise.";

            return callOpenAi(prompt);

        } catch (Exception e) {
            log.error("Live Log Fetch Failed", e);
            return "A360 서버에서 실시간 로그를 가져오는 데 실패했습니다.";
        }
    }

    private String generateGeneralAnswerWithRAG(String userMessage) {
        List<AiLearningLog> knowledgeBase = learningLogRepository.findAllByOrderByLearnedAtDesc();
        String context = knowledgeBase.stream()
                .limit(10)
                .map(log -> String.format("- [%s] %s: %s", log.getCategory(), log.getTargetName(),
                        log.getContentSummary()))
                .collect(Collectors.joining("\n"));

        if (context.isEmpty()) {
            context = "학습된 내부 데이터가 없습니다. 일반적인 지식으로 답변하세요.";
        }

        String prompt = "You are 'Charles', an RPA Operations Assistant.\n" +
                "Use the following [Internal Knowledge] to answer the user's question.\n" +
                "If the answer is not in the knowledge, say you don't know politely.\n\n" +
                "[Internal Knowledge]\n" + context + "\n\n" +
                "User: " + userMessage + "\n" +
                "Answer (in Korean):";

        return callOpenAi(prompt);
    }

    /**
     * OpenAI 호출 공통 메서드 (수정됨)
     */
    private String callOpenAi(String prompt) {
        OpenAiRequest request = new OpenAiRequest();
        request.setModel("gpt-4o-mini");

        // [수정] 0이 되지 않도록 설정값 주입
        request.setMaxTokens(defaultMaxTokens);

        request.addMessage("user", prompt);

        String json = openAiClient.call(request);
        try {
            JsonNode root = objectMapper.readTree(json);
            return root.path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            return "AI 응답 파싱 실패";
        }
    }

    private String getMockChatResponse(String question) {
        if (question.contains("안녕")) {
            return "안녕하세요! AI 비서 Charles입니다. (현재 오프라인 모드)";
        } else if (question.contains("오류")) {
            return "현재 OpenAI 연결이 지연되고 있어 **가상 데이터**로 답변드립니다.\n" +
                    "모니터링 결과: **Finance_Bot**에서 2건의 타임아웃이 발생했습니다.";
        } else {
            return "죄송합니다. 현재 AI 사용량 초과로 인해 상세 답변이 어렵습니다.\n" +
                    "하지만 시스템 모니터링은 정상적으로 수행 중입니다.";
        }
    }
}