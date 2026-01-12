package kr.co.xai.portal.backend.ai.openai;

import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class OpenAiClient {

    private final OpenAiProperties props;
    private final RestTemplate restTemplate;

    /**
     * OpenAI API 공통 호출
     * - 항상 String(JSON 원문)만 반환
     */
    public String call(OpenAiRequest request) {

        validate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(props.getKey());

        HttpEntity<OpenAiRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                props.getUrl(),
                HttpMethod.POST,
                entity,
                String.class);

        return response.getBody();
    }

    private void validate() {
        if (props.getKey() == null || props.getKey().isBlank()) {
            throw new IllegalStateException(
                    "OpenAI API Key 가 설정되지 않았습니다. (openai.api.key)");
        }
    }
}
