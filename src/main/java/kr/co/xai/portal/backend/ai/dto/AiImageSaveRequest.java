package kr.co.xai.portal.backend.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiImageSaveRequest {

    /**
     * [수정됨] Map 대신 구체적인 DTO 타입을 사용하여
     * 서비스 계층에서 .getStructuredData() 등의 메소드를 호출할 수 있게 합니다.
     */
    private AiImageAnalysisResponse analysisResult;

    /**
     * 선택: 원본 파일명
     */
    private String sourceFileName;
}