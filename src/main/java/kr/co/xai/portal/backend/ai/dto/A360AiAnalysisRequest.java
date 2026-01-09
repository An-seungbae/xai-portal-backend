package kr.co.xai.portal.backend.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class A360AiAnalysisRequest {

    private Map<String, Object> filter;
    private List<Map<String, String>> sort;
    private String botName;
    private String errorCode;
    private String message;
    private String occurredAt;
    /** 언어 (KO / EN) */
    private String language;
}
