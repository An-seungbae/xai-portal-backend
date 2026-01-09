package kr.co.xai.portal.backend.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Data
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiSmartSearchResponse {
    private String intent; // SCHEDULE, DEVICE, HISTORY
    private String summary; // AI 요약 멘트
    private Object data; // 실제 리스트 데이터 (Generic)
}