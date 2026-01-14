package kr.co.xai.portal.backend.ai.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // null인 필드는 API 전송 시 제외
public class OpenAiRequest {

    private String model;

    // 메시지 리스트 (User, Assistant, Tool 등)
    private List<Map<String, Object>> messages = new ArrayList<>();

    // [수정] int -> Integer (기본값 0이 전송되는 문제 해결)
    // Integer로 선언하면 값이 없을 때 null이 되며, @JsonInclude에 의해 JSON에서 생략됩니다.
    @JsonProperty("max_tokens")
    private Integer maxTokens;

    private Double temperature;

    // [Agent 기능] AI가 사용할 도구 목록
    private List<Map<String, Object>> tools;

    // [Agent 기능] 도구 선택 옵션 ("auto" 등)
    private Object tool_choice;

    // 생성자
    public OpenAiRequest(String model, Double temperature) {
        this.model = model;
        this.temperature = temperature;
    }

    // 1. 일반 텍스트 메시지 추가 (User, System)
    public void addMessage(String role, String content) {
        Map<String, Object> message = new HashMap<>();
        message.put("role", role);
        message.put("content", content);
        this.messages.add(message);
    }

    // 2. Assistant 메시지 추가 (Tool Calls 포함)
    public void addAssistantMessageWithToolCalls(Object toolCallsJsonNode) {
        Map<String, Object> message = new HashMap<>();
        message.put("role", "assistant");
        message.put("content", null);
        message.put("tool_calls", toolCallsJsonNode);
        this.messages.add(message);
    }

    // 3. Tool Output 메시지 추가 (도구 실행 결과)
    public void addToolOutputMessage(String toolCallId, String outputContent) {
        Map<String, Object> message = new HashMap<>();
        message.put("role", "tool");
        message.put("tool_call_id", toolCallId);
        message.put("content", outputContent);
        this.messages.add(message);
    }
}