package kr.co.xai.portal.backend.ai.openai.dto;

import lombok.Data;
import java.util.List;

@Data
public class OpenAiEmbeddingResponse {
    private List<EmbeddingData> data;

    @Data
    public static class EmbeddingData {
        private List<Float> embedding; // 1536차원 벡터
        private int index;
    }
}