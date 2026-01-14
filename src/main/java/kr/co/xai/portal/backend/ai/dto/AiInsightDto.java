package kr.co.xai.portal.backend.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiInsightDto {
    private int id;
    private String type; // schedule, resource, process
    private String title;
    private String problem;
    private String solution;
    private String expectedEffect;
    private int impactScore;
    private int timeSaveMin;
}