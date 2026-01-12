package kr.co.xai.portal.backend.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import kr.co.xai.portal.backend.ai.openai.OpenAiClient;
import kr.co.xai.portal.backend.ai.openai.OpenAiRequest;
import kr.co.xai.portal.backend.integration.a360.A360ActivityClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAgentService {

    private final OpenAiClient openAiClient;
    private final A360ActivityClient a360Client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // AI에게 제공할 도구(API) 정의
    private static final List<Map<String, Object>> AVAILABLE_TOOLS = new ArrayList<>();

    static {
        // 도구 1: 현재 실행 중인 봇 조회
        AVAILABLE_TOOLS.add(Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "get_running_jobs",
                        "description", "현재 A360에서 실행 중이거나 대기 중인 모든 봇(Job) 목록을 조회합니다.",
                        "parameters", Map.of("type", "object", "properties", Map.of(), "required", List.of()))));

        // 도구 2: 봇 실행 이력 조회
        AVAILABLE_TOOLS.add(Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "get_bot_history",
                        "description", "특정 봇의 최근 실행 이력, 성공/실패 여부, 수행 시간을 조회합니다.",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "botName", Map.of("type", "string", "description", "조회할 봇의 이름 (부분 일치 가능)")),
                                "required", List.of("botName")))));

        // 도구 3: 스케줄 조회
        AVAILABLE_TOOLS.add(Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "get_bot_schedule",
                        "description", "봇의 스케줄 정보(다음 실행 시간, 반복 주기 등)를 조회합니다.",
                        "parameters", Map.of("type", "object", "properties", Map.of(), "required", List.of()))));
    }

    /**
     * AI Agent 실행 (질문 -> 도구선택 -> 실행 -> 답변 루프)
     */
    public String runAgent(String userQuery) {
        log.info(">>> AI Agent Started. Query: {}", userQuery);

        // 1. 대화 히스토리 초기화
        List<Map<String, Object>> conversation = new ArrayList<>();
        conversation.add(Map.of(
                "role", "system",
                "content", "You are an intelligent A360 RPA Operation Assistant.\n" +
                        "You have access to tools to fetch real-time data.\n" +
                        "Use tools strictly when needed. Answer in Korean."));
        conversation.add(Map.of("role", "user", "content", userQuery));

        // 2. 사고 루프 (최대 5회 왕복 제한)
        int maxTurns = 5;
        for (int i = 0; i < maxTurns; i++) {
            // OpenAI 호출
            OpenAiRequest request = new OpenAiRequest();
            request.setModel("gpt-4o-mini");
            request.setMaxTokens(2000);
            request.setMessages(conversation);
            request.setTools(AVAILABLE_TOOLS);
            request.setTool_choice("auto");

            String responseJson = openAiClient.call(request);

            try {
                JsonNode root = objectMapper.readTree(responseJson);
                JsonNode choice = root.path("choices").get(0);
                JsonNode message = choice.path("message");
                String content = message.path("content").asText(null);
                JsonNode toolCalls = message.path("tool_calls");

                // AI 응답을 대화 기록에 추가
                Map<String, Object> aiMessage = new HashMap<>();
                aiMessage.put("role", "assistant");
                if (content != null)
                    aiMessage.put("content", content);
                if (!toolCalls.isMissingNode())
                    aiMessage.put("tool_calls", toolCalls);
                conversation.add(aiMessage);

                // 도구 호출이 없으면 최종 답변이므로 종료
                if (toolCalls.isMissingNode() || toolCalls.size() == 0) {
                    return content;
                }

                // 3. 도구 실행 및 결과 반환
                for (JsonNode toolCall : toolCalls) {
                    String functionName = toolCall.path("function").path("name").asText();
                    String arguments = toolCall.path("function").path("arguments").asText();
                    String toolCallId = toolCall.path("id").asText();

                    log.info(">>> AI Decided to call tool: {} with args: {}", functionName, arguments);

                    // 실제 Java 메소드 실행
                    String toolResult = executeTool(functionName, arguments);

                    // 결과를 대화에 추가 (Tool Role)
                    conversation.add(Map.of(
                            "role", "tool",
                            "tool_call_id", toolCallId,
                            "name", functionName,
                            "content", toolResult));
                }

            } catch (JsonProcessingException e) {
                log.error("Parsing Error", e);
                return "AI 응답 처리 중 오류가 발생했습니다.";
            }
        }

        return "생각이 너무 길어져서 중단되었습니다.";
    }

    /**
     * 실제 A360 API를 호출하는 헬퍼 메서드
     */
    private String executeTool(String functionName, String jsonArgs) {
        try {
            JsonNode argsNode = objectMapper.readTree(jsonArgs);

            if ("get_running_jobs".equals(functionName)) {
                List<Map<String, Object>> jobs = a360Client.fetchUnknownJobs();
                return jobs.isEmpty() ? "현재 실행 중인 봇이 없습니다." : objectMapper.writeValueAsString(jobs);
            } else if ("get_bot_history".equals(functionName)) {
                String botName = argsNode.path("botName").asText("");
                List<Map<String, Object>> logs = a360Client.fetchRecentLogs(botName, 7); // 최근 7일
                return logs.isEmpty() ? "해당 봇의 최근 이력이 없습니다." : objectMapper.writeValueAsString(logs);
            } else if ("get_bot_schedule".equals(functionName)) {
                // 스케줄 전체를 가져와서 문자열로 반환 (실제로는 필터링 필요할 수 있음)
                return objectMapper.writeValueAsString(a360Client.fetchSchedules());
            }

            return "Unknown function";
        } catch (Exception e) {
            log.error("Tool Execution Failed", e);
            return "Error executing tool: " + e.getMessage();
        }
    }
}