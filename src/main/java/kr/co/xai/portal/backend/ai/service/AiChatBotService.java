package kr.co.xai.portal.backend.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.xai.portal.backend.ai.dto.AiChatRequest;
import kr.co.xai.portal.backend.ai.dto.AiChatResponse;
import kr.co.xai.portal.backend.ai.entity.AiLearningLog;
import kr.co.xai.portal.backend.ai.openai.OpenAiClient;
import kr.co.xai.portal.backend.ai.openai.OpenAiRequest;
import kr.co.xai.portal.backend.ai.repository.AiLearningLogRepository;
import kr.co.xai.portal.backend.integration.a360.A360ActivityClient;
import kr.co.xai.portal.backend.integration.a360.dto.A360ActivityRequest;
import kr.co.xai.portal.backend.integration.a360.dto.A360ActivityResponse;
import kr.co.xai.portal.backend.integration.a360.dto.A360DeviceResponse;
import kr.co.xai.portal.backend.integration.a360.dto.A360LicenseResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatBotService {

    private final OpenAiClient openAiClient;
    private final A360ActivityClient a360ActivityClient;
    private final AiLearningLogRepository learningLogRepository; // [RAG] 지식 저장소
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${openai.api.max-tokens:1000}")
    private int defaultMaxTokens;

    /**
     * AI 챗봇 메인 로직
     */
    public AiChatResponse chat(AiChatRequest request) {
        String userMessage = request.getMessage();
        log.info("User Message: {}", userMessage);

        try {
            // 1. 의도 파악 (Routing)
            String intent = detectIntent(userMessage);
            log.info("Detected Intent: {}", intent);

            String systemContext = "";
            Object rawDataObj = null; // Frontend Chart/Table용 원본 데이터
            String dataContextStr = ""; // GPT Prompt용 텍스트 데이터

            // 2. 의도에 따른 API 호출 및 데이터 확보
            switch (intent) {
                case "BOT_STATUS":
                    rawDataObj = getBotStatusData(); // List 객체 반환
                    if (rawDataObj != null) {
                        dataContextStr = objectMapper.writeValueAsString(rawDataObj);
                        systemContext = "사용자가 '봇 상태(Device Status)'를 물어봤습니다. 아래 제공된 JSON 데이터를 기반으로 현재 연결된 봇, 연결 끊긴 봇, 상태 등을 요약해서 답변해.";
                    }
                    break;

                case "BOT_HISTORY":
                    rawDataObj = getAutomationHistoryData(); // List 객체 반환
                    if (rawDataObj != null) {
                        dataContextStr = objectMapper.writeValueAsString(rawDataObj);
                        systemContext = "사용자가 '최근 자동화 이력(Automation History)'을 물어봤습니다. 아래 제공된 JSON 데이터를 기반으로 최근 실행된 봇의 성공/실패 여부와 건수를 요약해서 답변해.";
                    }
                    break;

                case "ERROR_LOG": // [에러 정밀 분석]
                    rawDataObj = getRecentErrorData(); // List 객체 반환
                    if (rawDataObj != null && !((List<?>) rawDataObj).isEmpty()) {
                        dataContextStr = objectMapper.writeValueAsString(rawDataObj);
                        systemContext = "사용자가 '최근 오류 원인'을 물어봤습니다. 아래 제공된 [Failed Logs]를 분석하여 어떤 봇이 왜 실패했는지 한국어로 명확히 설명해.";
                    } else {
                        systemContext = "최근 24시간 내에 발견된 **오류 로그가 없습니다.** '시스템이 안정적입니다'라고 답변해.";
                        rawDataObj = null; // 데이터가 없으므로 rawData도 null
                    }
                    break;

                case "LICENSE_INFO":
                    rawDataObj = getLicenseData(); // Object 객체 반환
                    if (rawDataObj != null) {
                        dataContextStr = objectMapper.writeValueAsString(rawDataObj);
                        systemContext = "사용자가 'A360 라이선스 정보'를 물어봤습니다. " +
                                "아래 JSON 데이터를 분석하여 'Control Room'과 'Cognitive/IQ Bot' 관련 주요 라이선스 현황(구매 수량, 사용 수량)을 " +
                                "사용자가 보기 편하게 **Markdown 표(Table)** 형식으로 정리해서 보여줘. " +
                                "중요하지 않은 항목(count가 0인 것 등)은 제외하고 핵심만 요약해.";
                    }
                    break;

                default: // GENERAL_CHAT (RAG 적용)
                    // RAG 데이터는 구조화된 데이터(Chart)로 보기 어려우므로 rawDataObj는 null로 둠 (필요시 변경 가능)
                    dataContextStr = getRagContext();
                    systemContext = "너는 A360 RPA 포털의 유능한 AI 비서 '찰스'야. " +
                            "아래 [Internal Knowledge]를 참고하여 질문에 답변해. 정보가 없으면 정중히 모른다고 해.";
                    break;
            }

            // 3. 최종 답변 생성 (LLM w/ Data)
            String finalResponse = generateFinalResponse(userMessage, systemContext, dataContextStr);

            return AiChatResponse.builder()
                    .answer(finalResponse)
                    .intent(intent)
                    .rawData(rawDataObj) // [핵심] 차트/표 렌더링을 위한 원본 객체 전달
                    .build();

        } catch (HttpClientErrorException e) {
            // [예외처리] OpenAI Quota Exceeded (429) 등
            log.warn("OpenAI API Error: {}", e.getMessage());
            return AiChatResponse.builder()
                    .answer(getMockChatResponse(userMessage))
                    .intent("MOCK_RESPONSE")
                    .build();
        } catch (Exception e) {
            log.error("Chat Error", e);
            return AiChatResponse.builder()
                    .answer("죄송합니다, 주인님. 처리 도중 오류가 발생했습니다.\n" + e.getMessage())
                    .build();
        }
    }

    // =================================================================================
    // 🧠 Private Methods (AI Logic)
    // =================================================================================

    private String detectIntent(String message) {
        String prompt = "Classify the user's intent into one of the following categories:\n" +
                "- BOT_STATUS: Asking about bot agents, devices, connected status.\n" +
                "- BOT_HISTORY: Asking about general execution logs, past activities.\n" +
                "- ERROR_LOG: Asking about 'errors', 'failures', 'why bot stopped', 'bug'.\n" +
                "- LICENSE_INFO: Asking about licenses, purchased count.\n" +
                "- GENERAL_CHAT: General questions.\n\n" +
                "User Message: " + message + "\n\n" +
                "Respond ONLY with the category name (e.g., BOT_STATUS).";

        try {
            return callGpt(prompt).trim();
        } catch (Exception e) {
            log.error("Intent detection failed", e);
            // 키워드 기반 백업 로직
            if (message.contains("오류") || message.contains("에러") || message.contains("실패"))
                return "ERROR_LOG";
            if (message.contains("라이선스"))
                return "LICENSE_INFO";
            return "GENERAL_CHAT";
        }
    }

    private String generateFinalResponse(String userMessage, String systemInstruction, String dataContext) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(systemInstruction).append("\n\n");

        if (dataContext != null && !dataContext.isEmpty()) {
            prompt.append("=== [Context / Data] ===\n");
            prompt.append(dataContext).append("\n");
            prompt.append("========================\n\n");
        }

        prompt.append("User Question: ").append(userMessage).append("\n");
        prompt.append("Response (in Korean):");

        return callGpt(prompt.toString());
    }

    private String callGpt(String prompt) {
        OpenAiRequest req = new OpenAiRequest();
        req.setModel("gpt-4o-mini");
        req.setTemperature(0.3);
        req.setMaxTokens(defaultMaxTokens);

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", prompt));
        req.setMessages(messages);

        try {
            String resp = openAiClient.call(req);
            JsonNode node = objectMapper.readTree(resp);
            return node.path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            throw new RuntimeException("AI Service Call Failed", e);
        }
    }

    private String getMockChatResponse(String question) {
        if (question.contains("오류") || question.contains("에러")) {
            return "현재 AI 서버 연결이 지연되어 **가상 데이터**로 답변드립니다.\n" +
                    "모니터링 결과: **Finance_Bot**에서 타임아웃 오류가 감지되었습니다.";
        }
        return "죄송합니다. 현재 AI 사용량이 초과되어 잠시 후 다시 시도해주세요.";
    }

    // =================================================================================
    // 🔌 Private Methods (Data Fetching) - Return Object for Rich UI
    // =================================================================================

    /**
     * 1. 봇 상태 (Returns List)
     */
    private Object getBotStatusData() {
        try {
            A360DeviceResponse response = a360ActivityClient.fetchDevices();
            if (response == null || response.getList() == null)
                return Collections.emptyList();
            return response.getList();
        } catch (Exception e) {
            log.error("Failed to fetch devices", e);
            return null;
        }
    }

    /**
     * 2. 자동화 이력 (Returns List)
     */
    private Object getAutomationHistoryData() {
        try {
            A360ActivityRequest req = new A360ActivityRequest();
            A360ActivityRequest.Page page = new A360ActivityRequest.Page(0, 20);
            req.setPage(page);

            List<Map<String, Object>> sort = new ArrayList<>();
            Map<String, Object> sortItem = new HashMap<>();
            sortItem.put("field", "startDateTime");
            sortItem.put("direction", "desc");
            sort.add(sortItem);
            req.setSort(sort);

            A360ActivityResponse response = a360ActivityClient.fetchActivities(req);
            if (response == null || response.getList() == null)
                return Collections.emptyList();
            return response.getList();
        } catch (Exception e) {
            log.error("Failed to fetch activities", e);
            return null;
        }
    }

    /**
     * 3. 라이선스 정보 (Returns Object)
     */
    private Object getLicenseData() {
        try {
            A360LicenseResponse response = a360ActivityClient.fetchLicenses();
            if (response == null || response.getProducts() == null)
                return null;
            return response;
        } catch (Exception e) {
            log.error("Failed to fetch licenses", e);
            return null;
        }
    }

    /**
     * 4. [RAG] 사내 지식 검색 (Returns String Context)
     */
    private String getRagContext() {
        try {
            List<AiLearningLog> knowledgeBase = learningLogRepository.findAllByOrderByLearnedAtDesc();
            if (knowledgeBase.isEmpty())
                return "";

            return knowledgeBase.stream()
                    .limit(5)
                    .map(log -> String.format("- [%s] %s: %s", log.getCategory(), log.getTargetName(),
                            log.getContentSummary()))
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.warn("RAG Fetch Failed", e);
            return "";
        }
    }

    /**
     * 5. [ERROR] 최근 에러 정밀 조회 (Returns List<Map>)
     */
    private Object getRecentErrorData() {
        try {
            List<Map<String, Object>> logs = a360ActivityClient.fetchRecentLogs(null, 2); // 최근 2일

            // 실패/Unknown 상태만 필터링
            return logs.stream()
                    .filter(log -> {
                        String status = (String) log.get("status");
                        return status != null && (status.contains("FAIL") || status.contains("UNKNOWN"));
                    })
                    .limit(5)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to fetch recent errors", e);
            return Collections.emptyList();
        }
    }
}