package kr.co.xai.portal.backend.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true) // 핵심
public class A360AiAnalysisResponse {

    private String summary;
    private List<String> causeCandidates;
    private List<String> recommendedActions;
    private String businessMessage;

    // BOTH 선택 시 영문 분석
    private A360AiAnalysisResponse english;
}
