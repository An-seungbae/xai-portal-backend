package kr.co.xai.portal.backend.ai.openai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenAiEmbeddingRequest {
    private String model; // "text-embedding-3-small"
    private String input;
}