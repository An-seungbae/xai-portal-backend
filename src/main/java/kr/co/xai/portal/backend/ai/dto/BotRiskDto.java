package kr.co.xai.portal.backend.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Data
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BotRiskDto {
    private String botName; // 봇 이름
    private String department; // 부서
    private double riskScore; // 위험도 (0~100%)
    private String status; // 상태 (CRITICAL, WARNING, NORMAL)
    private double avgDuration; // 평소 평균 소요시간 (분)
    private double recentDuration; // 최근 소요시간 (분)
    private String predictedFailure; // 예상 장애 발생 시점 (예: "2일 내")
    private String analysisReport; // AI 분석 코멘트

    // 시각화를 위한 최근 7일간 실행 시간 추이 (분 단위)
    private List<Double> durationHistory;
}