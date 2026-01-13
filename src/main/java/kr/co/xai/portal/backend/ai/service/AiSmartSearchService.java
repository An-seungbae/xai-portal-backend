package kr.co.xai.portal.backend.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.xai.portal.backend.ai.dto.AiSmartSearchResponse;
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

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiSmartSearchService {

    private final OpenAiClient openAiClient;
    private final A360ActivityClient a360ActivityClient;
    private final AiLearningLogRepository learningLogRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${openai.api.max-tokens:2000}")
    private int defaultMaxTokens;

    // [핵심] 도구(API) 레지스트리: AI가 사용할 수 있는 능력들의 목록
    private final Map<String, ToolDefinition> toolRegistry = new HashMap<>();

    // 도구 정의 클래스
    private static class ToolDefinition {
        String name;
        String description;
        Function<String, Object> executor; // 실행 로직

        public ToolDefinition(String name, String description, Function<String, Object> executor) {
            this.name = name;
            this.description = description;
            this.executor = executor;
        }
    }

    /**
     * 서버 시작 시 AI가 사용할 수 있는 API 도구들을 자동 등록합니다.
     * 향후 API가 추가되면 여기에 한 줄만 추가하면 AI가 자동으로 인식합니다.
     */
    @PostConstruct
    public void initializeTools() {
        // 1. 봇 상태 조회
        toolRegistry.put("BOT_STATUS", new ToolDefinition(
                "BOT_STATUS",
                "Get current status of all bots/devices (Connected, Disconnected, etc). Keywords: bot status, device, connection.",
                (arg) -> a360ActivityClient.fetchDevices().getList()));

        // 2. 자동화 이력 조회
        toolRegistry.put("BOT_HISTORY", new ToolDefinition(
                "BOT_HISTORY",
                "Get historical execution logs of automations. Keywords: history, logs, execution, fail, success.",
                (arg) -> {
                    A360ActivityRequest req = new A360ActivityRequest();
                    req.setPage(new A360ActivityRequest.Page(0, 20));
                    return a360ActivityClient.fetchActivities(req).getList();
                }));

        // 3. 에러 로그 분석
        toolRegistry.put("ERROR_LOG", new ToolDefinition(
                "ERROR_LOG",
                "Get recent failed or unknown job logs for error analysis. Keywords: error, failure, problem, bug.",
                (arg) -> a360ActivityClient.fetchRecentLogs(null, 3)));

        // 4. 라이선스 정보
        toolRegistry.put("LICENSE_INFO", new ToolDefinition(
                "LICENSE_INFO",
                "Get A360 license usage and availability. Keywords: license, purchased, used, count.",
                (arg) -> a360ActivityClient.fetchLicenses()));

        // 5. [벡터 DB] 사내 지식 검색 (기본적으로 항상 수행하지만, 명시적 도구로도 등록)
        toolRegistry.put("KNOWLEDGE_BASE", new ToolDefinition(
                "KNOWLEDGE_BASE",
                "Search internal documents/vectors for manuals, guides, and past issues.",
                (arg) -> getVectorSearchResults(arg) // 인자로 검색어 전달
        ));
    }

    /**
     * 스마트 검색 메인 메서드
     */
    public AiSmartSearchResponse searchGlobal(String query) {
        log.info(">> Smart Search Query: {}", query);

        try {
            // 1. AI에게 어떤 도구가 필요한지 물어봅니다. (Planner)
            List<String> requiredTools = determineRequiredTools(query);
            log.info(">> AI Decided to use tools: {}", requiredTools);

            Map<String, Object> aggregatedResults = new HashMap<>();
            StringBuilder contextBuilder = new StringBuilder();

            // 2. 선택된 도구들을 실행하여 데이터 수집 (Execution)
            // 항상 KNOWLEDGE_BASE는 기본 포함하거나 AI 판단에 따름
            if (!requiredTools.contains("KNOWLEDGE_BASE")) {
                requiredTools.add("KNOWLEDGE_BASE");
            }

            for (String toolName : requiredTools) {
                ToolDefinition tool = toolRegistry.get(toolName);
                if (tool != null) {
                    try {
                        Object result = tool.executor.apply(query);
                        if (result != null) {
                            aggregatedResults.put(toolName, result);
                            String jsonResult = objectMapper.writeValueAsString(result);
                            // 프롬프트에 넣을 데이터 요약 (너무 길면 잘라냄)
                            contextBuilder.append(String.format("\n=== [%s Data] ===\n%s\n", toolName,
                                    StringUtils.abbreviate(jsonResult, 3000)));
                        }
                    } catch (Exception e) {
                        log.error("Tool execution failed: {}", toolName, e);
                    }
                }
            }

            // 3. 수집된 데이터를 바탕으로 최종 답변 생성 (Synthesis)
            String summary = generateSummary(query, contextBuilder.toString());

            // 4. 결과 반환 (원본 데이터 + 요약)
            return AiSmartSearchResponse.builder()
                    .query(query)
                    .summary(summary)
                    .data(aggregatedResults) // 프론트엔드에서 차트/표로 그릴 원본 데이터 맵
                    .build();

        } catch (Exception e) {
            log.error("Smart Search Failed", e);
            return AiSmartSearchResponse.builder()
                    .query(query)
                    .summary("검색 중 오류가 발생했습니다: " + e.getMessage())
                    .build();
        }
    }

    // --- Private Methods ---

    /**
     * AI에게 쿼리를 분석시켜 필요한 도구 목록을 받아옵니다.
     */
    private List<String> determineRequiredTools(String query) {
        StringBuilder toolDesc = new StringBuilder();
        toolRegistry.forEach((k, v) -> toolDesc.append(String.format("- %s: %s\n", k, v.description)));

        String prompt = "You are a Smart Search Agent. Analyze the user query and select the relevant API tools to fetch data.\n"
                +
                "Available Tools:\n" + toolDesc.toString() +
                "\nUser Query: \"" + query + "\"\n" +
                "Response Format: Return ONLY a comma-separated list of Tool Names (e.g., BOT_STATUS, ERROR_LOG). If unsure, include KNOWLEDGE_BASE.";

        try {
            String response = callGpt(prompt, 0.0); // Temperature 0 for logic
            return Arrays.stream(response.split(","))
                    .map(String::trim)
                    .filter(toolRegistry::containsKey)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            // 실패 시 기본적으로 지식 검색만 수행
            return new ArrayList<>(List.of("KNOWLEDGE_BASE"));
        }
    }

    /**
     * 최종 요약 생성
     */
    private String generateSummary(String query, String context) {
        if (context.trim().isEmpty()) {
            return "검색 결과가 없습니다.";
        }
        String prompt = "User Query: " + query + "\n\n" +
                "Integrated Data from Systems:\n" + context + "\n\n" +
                "Instruction: Based on the data above, provide a comprehensive answer in Korean. " +
                "Cite the source (e.g., [Bot Status], [Knowledge Base]) when mentioning specific facts. " +
                "Use Markdown formatting.";

        return callGpt(prompt, 0.5);
    }

    private String callGpt(String prompt, double temperature) {
        OpenAiRequest req = new OpenAiRequest();
        req.setModel("gpt-4o-mini"); // 빠르고 똑똑한 모델
        req.setTemperature(temperature);
        req.addMessage("user", prompt);

        try {
            String resp = openAiClient.call(req);
            JsonNode node = objectMapper.readTree(resp);
            return node.path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            log.error("GPT Call Failed", e);
            throw new RuntimeException("AI processing failed");
        }
    }

    // 벡터 DB 검색 시뮬레이션 (실제로는 Repository 호출)
    private Object getVectorSearchResults(String query) {
        List<AiLearningLog> logs = learningLogRepository.findAllByOrderByLearnedAtDesc(); // 실제론 검색 로직 필요
        // 간단한 키워드 필터링 (임시)
        return logs.stream()
                .filter(log -> log.getContentSummary().contains(query) || log.getTargetName().contains(query))
                .limit(5)
                .map(l -> Map.of("title", l.getTargetName(), "summary", l.getContentSummary(), "category",
                        l.getCategory()))
                .collect(Collectors.toList());
    }

    // StringUtils.abbreviate helper
    private static class StringUtils {
        static String abbreviate(String str, int maxWidth) {
            if (str == null)
                return "";
            if (str.length() <= maxWidth)
                return str;
            return str.substring(0, maxWidth) + "...";
        }
    }
}