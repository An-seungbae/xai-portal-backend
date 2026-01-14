package kr.co.xai.portal.backend.ai.openai;

import kr.co.xai.portal.backend.ai.openai.dto.OpenAiChatResponse;
import kr.co.xai.portal.backend.ai.openai.dto.OpenAiEmbeddingRequest;
import kr.co.xai.portal.backend.ai.openai.dto.OpenAiEmbeddingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List; // [수정] List 임포트 추가

@Component
@RequiredArgsConstructor
public class OpenAiClient {

    private final OpenAiProperties props;
    private final RestTemplate restTemplate;

    /**
     * 기본 호출: JSON String 반환
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

    /**
     * Chat Completion 호출: OpenAiChatResponse 객체 반환
     */
    public OpenAiChatResponse chat(OpenAiRequest request) {
        validate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(props.getKey());

        HttpEntity<OpenAiRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<OpenAiChatResponse> response = restTemplate.exchange(
                props.getUrl(),
                HttpMethod.POST,
                entity,
                OpenAiChatResponse.class);

        return response.getBody();
    }

    /**
     * [추가] 텍스트 임베딩 생성 (Text -> Vector)
     * Model: text-embedding-3-small (1536 dimensions)
     */
    public List<Float> getEmbedding(String text) {
        validate();

        // 공백이나 줄바꿈을 정리하여 벡터 품질 향상
        String cleanedText = text.replaceAll("\\s+", " ").trim();

        OpenAiEmbeddingRequest request = OpenAiEmbeddingRequest.builder()
                .model("text-embedding-3-small")
                .input(cleanedText)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(props.getKey());

        HttpEntity<OpenAiEmbeddingRequest> entity = new HttpEntity<>(request, headers);

        // 임베딩 API 엔드포인트 호출
        ResponseEntity<OpenAiEmbeddingResponse> response = restTemplate.exchange(
                "https://api.openai.com/v1/embeddings",
                HttpMethod.POST,
                entity,
                OpenAiEmbeddingResponse.class);

        if (response.getBody() != null && !response.getBody().getData().isEmpty()) {
            return response.getBody().getData().get(0).getEmbedding();
        }

        throw new RuntimeException("Embedding 생성 실패: API 응답이 비어있습니다.");
    }

    private void validate() {
        if (props.getKey() == null || props.getKey().isBlank()) {
            throw new IllegalStateException(
                    "OpenAI API Key 가 설정되지 않았습니다. (openai.api.key)");
        }
    }
}