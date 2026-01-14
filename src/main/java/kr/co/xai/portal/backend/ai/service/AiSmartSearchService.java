package kr.co.xai.portal.backend.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.xai.portal.backend.ai.annotation.AiTool;
import kr.co.xai.portal.backend.ai.dto.AiSmartSearchResponse;
import kr.co.xai.portal.backend.ai.openai.OpenAiClient;
import kr.co.xai.portal.backend.ai.openai.OpenAiRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiSmartSearchService {

    private final OpenAiClient openAiClient;
    private final ApplicationContext applicationContext; // [핵심] 모든 Bean을 검색하기 위해 필요
    private final ObjectMapper objectMapper = new ObjectMapper();

    // AI에게 보낼 도구 명세서 리스트
    private final List<Map<String, Object>> toolsSpec = new ArrayList<>();

    // AI가 요청하면 실제로 실행할 함수 맵 (함수명 -> 실행로직)
    private final Map<String, Function<JsonNode, Object>> toolExecutors = new HashMap<>();

    /**
     * [자동화 엔진]
     * 서버가 시작될 때 @AiTool 어노테이션이 붙은 모든 메서드를 찾아서 AI에게 가르칩니다.
     * 이제 수동으로 registerTool을 호출할 필요가 없습니다.
     */
    @PostConstruct
    public void initAutoDiscovery() {
        log.info("🔎 Starting AI Tool Auto-Discovery...");

        // 1. 스프링에 등록된 모든 Bean 이름 가져오기
        String[] beanNames = applicationContext.getBeanDefinitionNames();

        for (String beanName : beanNames) {
            try {
                Object bean = applicationContext.getBean(beanName);
                // AOP 프록시 객체일 경우 실제 클래스 확인 (필요시)
                Class<?> beanClass = bean.getClass();

                // 2. 메서드 전수 조사
                for (Method method : beanClass.getMethods()) {
                    if (method.isAnnotationPresent(AiTool.class)) {
                        // @AiTool이 붙은 메서드 발견! 등록 진행
                        registerMethodAsTool(bean, method);
                    }
                }
            } catch (Exception e) {
                // 특정 빈 로드 실패는 무시하고 계속 진행 (시스템 빈 등)
                log.trace("Skipping bean {}: {}", beanName, e.getMessage());
            }
        }
        log.info("✅ AI Agent is ready with {} tools.", toolsSpec.size());
    }

    /**
     * 발견된 Java 메서드를 OpenAI 도구 형식(JSON Schema)으로 변환하여 등록
     */
    private void registerMethodAsTool(Object bean, Method method) {
        AiTool annotation = method.getAnnotation(AiTool.class);
        String functionName = method.getName(); // 함수 이름 (예: searchRealTimeNews)
        String description = annotation.description();

        log.info("   + Registering Tool: {}", functionName);

        // 1. 파라미터 분석 -> JSON Schema 자동 생성
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        Map<String, Object> props = new HashMap<>();

        // 메서드의 파라미터들을 하나씩 까봅니다.
        for (Parameter param : method.getParameters()) {
            String paramName = param.getName(); // 주의: 컴파일 시 -parameters 옵션이 없으면 arg0 등으로 나올 수 있음

            // 파라미터 타입에 따른 스키마 정의 (기본 string, 숫자면 integer)
            String type = "string";
            if (param.getType() == int.class || param.getType() == Integer.class)
                type = "integer";
            else if (param.getType() == boolean.class || param.getType() == Boolean.class)
                type = "boolean";

            props.put(paramName, Map.of("type", type, "description", "Parameter " + paramName));
        }
        parameters.put("properties", props);

        // 2. OpenAI Tools Spec에 추가
        Map<String, Object> function = new HashMap<>();
        function.put("name", functionName);
        function.put("description", description);
        function.put("parameters", parameters);

        Map<String, Object> tool = new HashMap<>();
        tool.put("type", "function");
        tool.put("function", function);
        toolsSpec.add(tool);

        // 3. 실행 로직(Executor) 등록 (Reflection 사용)
        toolExecutors.put(functionName, (jsonArgs) -> {
            try {
                // AI가 준 JSON 파라미터를 Java 객체로 변환
                Object[] args = new Object[method.getParameterCount()];
                Parameter[] methodParams = method.getParameters();

                for (int i = 0; i < methodParams.length; i++) {
                    String paramName = methodParams[i].getName();
                    Class<?> paramType = methodParams[i].getType();

                    // JSON에 해당 파라미터가 있으면 변환해서 넣고, 없으면 null
                    if (jsonArgs.has(paramName)) {
                        args[i] = objectMapper.treeToValue(jsonArgs.get(paramName), paramType);
                    } else {
                        args[i] = null;
                    }
                }
                // 실제 메서드 실행 (invoke)
                return method.invoke(bean, args);
            } catch (Exception e) {
                log.error("Tool Execution Failed: {}", functionName, e);
                return "Error: " + e.getMessage();
            }
        });
    }

    /**
     * 메인 검색 메서드 (Agent Loop) - 기존 로직 유지
     */
    public AiSmartSearchResponse searchGlobal(String userQuery) {
        log.info(">> Agent Start: {}", userQuery);

        OpenAiRequest request = new OpenAiRequest();
        request.setModel("gpt-4o-mini");
        request.setTools(toolsSpec);
        request.setTool_choice("auto");
        request.setMaxTokens(2000);
        request.setTemperature(0.0);

        request.addMessage("system",
                "You are an AI Assistant for A360 RPA. Today is " + LocalDate.now() + ". Use tools to fetch data.");
        request.addMessage("user", userQuery);

        Map<String, Object> aggregatedData = new HashMap<>();
        String finalAnswer = "";

        try {
            // 최대 4번 왕복 (Think -> Act -> Observe -> Think ...)
            for (int i = 0; i < 4; i++) {
                String responseBody = openAiClient.call(request);
                JsonNode rootNode = objectMapper.readTree(responseBody);
                JsonNode choice = rootNode.path("choices").get(0);
                JsonNode message = choice.path("message");

                // AI가 도구를 쓰겠다고 함?
                if (message.has("tool_calls")) {
                    JsonNode toolCalls = message.get("tool_calls");
                    request.addAssistantMessageWithToolCalls(toolCalls);

                    for (JsonNode toolCall : toolCalls) {
                        String functionName = toolCall.path("function").path("name").asText();
                        String arguments = toolCall.path("function").path("arguments").asText();
                        String toolCallId = toolCall.path("id").asText();

                        log.info(">> AI executes tool: {} with args: {}", functionName, arguments);

                        // 도구 실행 (여기서 위에서 등록한 Reflection 로직이 돕니다)
                        Object result = executeTool(functionName, arguments);

                        aggregatedData.put(functionName.toUpperCase(), result);
                        request.addToolOutputMessage(toolCallId, objectMapper.writeValueAsString(result));
                    }
                } else {
                    // 최종 답변
                    finalAnswer = message.path("content").asText();
                    break;
                }
            }
        } catch (Exception e) {
            log.error("Agent Loop Error", e);
            finalAnswer = "Error: " + e.getMessage();
        }

        return AiSmartSearchResponse.builder()
                .query(userQuery)
                .summary(finalAnswer)
                .data(aggregatedData)
                .build();
    }

    private Object executeTool(String name, String jsonArgs) {
        if (!toolExecutors.containsKey(name))
            return "Error: Unknown tool '" + name + "'";
        try {
            JsonNode argsNode = objectMapper.readTree(jsonArgs);
            return toolExecutors.get(name).apply(argsNode);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}