package kr.co.xai.portal.backend.ai.service;

import kr.co.xai.portal.backend.ai.entity.AiLearningLog;
import kr.co.xai.portal.backend.ai.openai.OpenAiClient;
import kr.co.xai.portal.backend.ai.openai.OpenAiRequest;
import kr.co.xai.portal.backend.ai.repository.AiLearningLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiCodeReviewService {

    private final OpenAiClient openAiClient;
    private final AiLearningLogRepository learningLogRepository;

    /**
     * 코드 파일(.txt, .json, .bot 등)을 받아 AI 리뷰를 수행합니다.
     */
    public String reviewCodeFile(MultipartFile file) {
        try {
            // 1. 코드 파일 내용 읽기
            String codeContent = new BufferedReader(
                    new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));

            // 2. [핵심] 학습된 지식 가져오기 (RAG)
            // 최근 학습한 A360 자산 정보(봇 스케줄, 디바이스 등)를 조회하여 컨텍스트로 제공
            List<AiLearningLog> knowledgeBase = learningLogRepository.findAllByOrderByLearnedAtDesc();

            // 토큰 절약을 위해 최근 20건의 핵심 지식만 요약
            String knowledgeContext = knowledgeBase.stream()
                    .limit(20)
                    .map(log -> String.format("- [%s] %s: %s", log.getCategory(), log.getTargetName(),
                            log.getContentSummary()))
                    .collect(Collectors.joining("\n"));

            if (knowledgeContext.isEmpty()) {
                knowledgeContext = "학습된 내부 자산 정보가 없습니다.";
            }

            // 3. 프롬프트 구성 (코드 + 지식)
            String prompt = "You are 'The Code Doctor', an expert RPA Code Reviewer.\n\n" +
                    "I will provide you with **RPA Code** and **Internal Knowledge Base** (Learned Data).\n" +
                    "Your task is to review the code based on Best Practices AND check for consistency with our Internal Knowledge.\n\n"
                    +
                    "### [Internal Knowledge Base]\n" +
                    "(Use this data to validate bot names, schedules, or device references in the code)\n" +
                    knowledgeContext + "\n\n" +
                    "### [Source Code]\n" +
                    codeContent + "\n\n" +
                    "### Instructions:\n" +
                    "1. Analyze the code for **Security Risks** (hardcoded passwords, IPs).\n" +
                    "2. Check for **Inefficiencies** (long delays, redundant loops).\n" +
                    "3. **Validate against Internal Knowledge**: If the code mentions a Bot Name or Device not found in the Knowledge Base, warn the user.\n"
                    +
                    "4. Output the result in **Korean** with Markdown format.";

            // 4. AI 호출
            OpenAiRequest request = new OpenAiRequest();
            request.setModel("gpt-4o"); // 코드 분석은 성능 좋은 모델 권장
            request.addMessage("system", "You are a strict and helpful Code Reviewer.");
            request.addMessage("user", prompt);

            return openAiClient.call(request);

        } catch (Exception e) {
            log.error("Code Review Failed", e);
            return "코드 분석 중 오류가 발생했습니다: " + e.getMessage();
        }
    }
}