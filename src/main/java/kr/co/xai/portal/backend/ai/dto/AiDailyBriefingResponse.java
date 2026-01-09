package kr.co.xai.portal.backend.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDailyBriefingResponse {

    // AI가 생성한 상세 리포트 (HTML or Markdown 포맷)
    private String briefingMessage;

    // ---실시간 대시보드 통계 데이터 ---
    private int totalExecutions; // 금일 총 실행 수
    private int successCount; // 성공 수
    private int failedCount; // 실패 수
    private double successRate; // 성공률 (%)

    private int aiAnalysisCount; // 금일 AI가 분석한 건수 (나의 작업량)
    private int pendingErrors; // 아직 조치되지 않은 에러 (실패 - 분석수)
}