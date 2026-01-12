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
@JsonInclude(JsonInclude.Include.NON_NULL) // null인 필드는 API 전송 시 제외 (중요)
public class OpenAiRequest {

    private String model;

    // [중요] AI Agent의 Tool Call 결과(JSON 객체)를 담기 위해 Value 타입을 Object로 설정
    private List<Map<String, Object>> messages = new ArrayList<>();

    // [스타일 적용] Java 표준 CamelCase 사용 + JSON 매핑
    @JsonProperty("max_tokens")
    private int maxTokens;

    // [Agent 기능] AI가 사용할 도구 목록
    private List<Map<String, Object>> tools;

    // [Agent 기능] 도구 선택 옵션 ("auto" 등)
    private Object tool_choice;

    // [Helper] 일반 텍스트 메시지 추가용 (주인님 코드 반영)
    public void addMessage(String role, String content) {
        Map<String, Object> message = new HashMap<>();
        message.put("role", role);
        message.put("content", content);
        this.messages.add(message);
    }

    // [Helper] 고급 메시지 추가용 (Agent/Tool 메시지)
    public void addMessage(Map<String, Object> message) {
        this.messages.add(message);
    }
}