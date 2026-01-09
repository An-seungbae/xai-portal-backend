package kr.co.xai.portal.backend.ai.service;

import kr.co.xai.portal.backend.ai.dto.A360AiAnalysisResponse;
import kr.co.xai.portal.backend.ai.dto.AiImageAnalysisResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Service
public class AiImageAnalysisService {

    private final AiImageOcrService ocrService;
    private final A360AiAnalysisService analysisService;
    private final AiImageStructuredService structuredService;

    public AiImageAnalysisService(
            AiImageOcrService ocrService,
            A360AiAnalysisService analysisService,
            AiImageStructuredService structuredService) {
        this.ocrService = ocrService;
        this.analysisService = analysisService;
        this.structuredService = structuredService;
    }

    /**
     * [변경] 이미지와 텍스트 프롬프트를 통합 분석
     */
    public AiImageAnalysisResponse analyze(MultipartFile image, String prompt, String language) {

        StringBuilder analysisSourceText = new StringBuilder();
        String ocrRawText = null;

        // 1️⃣ 이미지 처리 (이미지가 있는 경우에만 수행)
        if (image != null && !image.isEmpty()) {
            ocrRawText = ocrService.extractRawText(image);
            String cleanOcr = ocrService.cleanText(ocrRawText);
            analysisSourceText.append(cleanOcr);
        }

        // 2️⃣ 텍스트 프롬프트 병합
        if (prompt != null && !prompt.trim().isEmpty()) {
            if (analysisSourceText.length() > 0) {
                analysisSourceText.append("\n\n[사용자 질문/요청]\n");
            }
            analysisSourceText.append(prompt);
        }

        // 3️⃣ 유효성 검사 (아무것도 입력되지 않은 경우)
        if (analysisSourceText.length() == 0) {
            throw new IllegalArgumentException("분석할 이미지 또는 텍스트를 입력해주세요.");
        }

        String finalText = analysisSourceText.toString();

        // 4️⃣ 데이터 구조화 및 AI 분석
        // 텍스트만 있어도 구조화 및 분석을 시도합니다.
        Map<String, Object> structuredData = structuredService.extractStructuredData(finalText);
        A360AiAnalysisResponse ai = analysisService.analyzeFromOcrText(finalText, language);

        // 5️⃣ 응답 생성
        AiImageAnalysisResponse response = new AiImageAnalysisResponse();
        response.setOcrRawText(ocrRawText != null ? ocrRawText : "(이미지 없음 - 텍스트 기반 분석)");
        response.setOcrCleanText(finalText);
        response.setStructuredData(structuredData);
        response.setSummary(ai.getSummary());
        response.setCauseCandidates(ai.getCauseCandidates());
        response.setRecommendedActions(ai.getRecommendedActions());
        response.setBusinessMessage(ai.getBusinessMessage());

        return response;
    }
}