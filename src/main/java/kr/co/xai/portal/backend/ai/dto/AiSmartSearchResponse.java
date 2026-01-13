package kr.co.xai.portal.backend.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.Map;

@Data
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiSmartSearchResponse {
    // private String intent; // SCHEDULE, DEVICE, HISTORY
    private String query; // [수정] 사용자가 입력한 검색어 (intent 대신 사용)
    private String summary; // AI 요약 멘트
    private Map<String, Object> data; // 각 툴별 원본 데이터 (key: BOT_STATUS 등)
}