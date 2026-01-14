package kr.co.xai.portal.backend.ai.service;

import kr.co.xai.portal.backend.ai.openai.OpenAiClient;
import kr.co.xai.portal.backend.ai.openai.OpenAiRequest;
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
    private final AiVectorService vectorService;

    /**
     * 코드 파일(.txt, .json, .bot 등)을 받아 RAG 기반 AI 리뷰를 수행합니다.
     */
    public String reviewCodeFile(MultipartFile file) {
        try {
            // 1. 코드 파일 내용 읽기
            String codeContent = new BufferedReader(
                    new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));

            // 2. [RAG 핵심] 벡터 DB 기반 지식 검색
            // 코드의 앞부분(요약)이나 전체를 쿼리로 사용하여, 가장 연관성 높은 사내 가이드/규정을 찾습니다.
            // (코드가 너무 길면 앞부분 300자만 쿼리로 사용하여 검색 속도 및 정확도 최적화)
            String query = "RPA Code Security & Best Practice Check: " +
                    (codeContent.length() > 300 ? codeContent.substring(0, 300) : codeContent);

            // Pinecone에서 유사도가 높은 상위 5개 문서를 가져옵니다.
            List<String> relevantDocs = vectorService.searchSimilarDocuments(query, 5);

            String knowledgeContext;
            if (relevantDocs == null || relevantDocs.isEmpty()) {
                knowledgeContext = "연관된 학습 문서(Knowledge Base)가 없습니다. 일반적인 RPA Best Practice 기준으로 리뷰합니다.";
            } else {
                // 검색된 문서들을 프롬프트에 넣기 좋게 포맷팅
                knowledgeContext = String.join("\n\n--- [Internal Reference / Guideline] ---\n", relevantDocs);
            }

            // 3. 프롬프트 구성 (코드 + 검색된 사내 지식)
            String prompt = "You are 'The Code Doctor', an expert RPA Code Reviewer.\n\n" +
                    "I will provide you with **RPA Code** and **Retrieved Internal Knowledge** (RAG).\n" +
                    "Your task is to review the code based on Best Practices AND check for consistency with our Internal Knowledge.\n\n"
                    +
                    "### [Retrieved Internal Knowledge]\n" +
                    "(These are the company's specific guidelines or similar past cases found in our Vector DB)\n" +
                    knowledgeContext + "\n\n" +
                    "### [Source Code]\n" +
                    codeContent + "\n\n" +
                    "### Instructions:\n" +
                    "1. **Security Check**: Look for hardcoded passwords, sensitive IPs, or non-compliant logic based on the [Retrieved Internal Knowledge].\n"
                    +
                    "2. **Optimization**: Check for long delays, redundant loops, or resource leaks.\n" +
                    "3. **Consistency**: If the code violates any rule found in the Knowledge Base, explicitly cite the rule.\n"
                    +
                    "4. Output the result in **Korean** with Markdown format (Use sections like '🚨 보안 경고', '💡 최적화 제안', '✅ 모범 사례').";

            // 4. AI 호출
            OpenAiRequest request = new OpenAiRequest();
            request.setModel("gpt-4o");
            request.setMaxTokens(4000); // 충분한 답변 길이를 위해 설정

            request.addMessage("system",
                    "You are a strict and helpful Code Reviewer. You always cite internal guidelines if applicable.");
            request.addMessage("user", prompt);

            return openAiClient.call(request);

        } catch (Exception e) {
            log.error("Code Review Failed", e);
            return "코드 분석 중 오류가 발생했습니다: " + e.getMessage();
        }
    }
}