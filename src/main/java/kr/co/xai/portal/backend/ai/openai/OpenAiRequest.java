package kr.co.xai.portal.backend.ai.openai;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Getter
@Setter
public class OpenAiRequest {

    private String model;
    private List<Map<String, String>> messages = new ArrayList<>();

    // [추가됨] 토큰 제한 설정 (OpenAI API 필드명: max_tokens)
    @JsonProperty("max_tokens")
    private int maxTokens;

    // 필요 시 temperature 등 추가 가능
    // private double temperature = 0.7;

    public void addMessage(String role, String content) {
        Map<String, String> message = new HashMap<>();
        message.put("role", role);
        message.put("content", content);
        this.messages.add(message);
    }
}