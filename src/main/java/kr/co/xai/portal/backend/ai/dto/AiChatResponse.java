package kr.co.xai.portal.backend.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatResponse {
    private String answer; // AI의 텍스트 답변
    private String intent; // 디버깅용 (ex: ERROR_SEARCH, GENERAL)
    private Object rawData; // [신규] 차트/표 렌더링을 위한 원본 데이터 (List or Map)
}