package kr.co.xai.portal.backend.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.xai.portal.backend.ai.dto.AiInsightDto;
import kr.co.xai.portal.backend.ai.openai.OpenAiClient;
import kr.co.xai.portal.backend.ai.openai.OpenAiRequest;
import kr.co.xai.portal.backend.ai.openai.dto.OpenAiChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiSmartInsightService {

    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;

    /**
     * 4대 핵심 API 데이터를 기반으로 무조건적인 최적화 시나리오를 도출합니다.
     */
    public List<AiInsightDto> analyzeAndGenerateInsights() {
        try {
            // 1. 4대 핵심 운영 데이터 수집 (시뮬레이션 데이터)
            String operationalData = gatherDeepOperationalData();

            // 2. OpenAI 프롬프트 구성 (강력한 페르소나 주입)
            String systemPrompt = """
                    You are 'Charles', an elite RPA Optimization Consultant.
                    Your goal is to find 'Optimization Opportunities' in any situation.
                    Even if the system looks stable, you MUST find:
                    1. Micro-delays in schedules.
                    2. Underutilized licenses or devices.
                    3. Potential bottleneck risks in high-volume processes.

                    Never say 'No optimization needed'. Always provide 3~4 actionable scenarios.
                    """;

            String userPrompt = String.format("""
                    Analyze the following 4-Dimensions of A360 Operational Data:
                    %s

                    Based on this, generate a JSON array of insights.
                    The 'solution' must be very specific (e.g., 'Change schedule from 09:00 to 08:30').

                    Output format (JSON Only):
                    [
                      {
                        "id": 1,
                        "type": "schedule" | "resource" | "process",
                        "title": "Title (Korean)",
                        "problem": "Problem Description (Korean)",
                        "solution": "Detailed Solution (Korean)",
                        "expectedEffect": "Effect (Korean, e.g., '대기시간 0분')",
                        "impactScore": 80-99,
                        "timeSaveMin": integer
                      }
                    ]
                    """, operationalData);

            // 3. OpenAI 호출
            OpenAiRequest request = new OpenAiRequest("gpt-4o", 0.7);
            request.addMessage("system", systemPrompt);
            request.addMessage("user", userPrompt);
            // maxTokens는 OpenAiRequest 수정(Integer)으로 해결됨

            OpenAiChatResponse response = openAiClient.chat(request);

            if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
                log.warn("OpenAI response is empty.");
                return getDefaultFallbackInsights();
            }

            // 4. 응답 파싱
            String content = response.getChoices().get(0).getMessage().getContent();
            content = refineJsonContent(content);

            return objectMapper.readValue(content, new TypeReference<List<AiInsightDto>>() {
            });

        } catch (Exception e) {
            log.error("Failed to generate AI insights", e);
            return getDefaultFallbackInsights();
        }
    }

    /**
     * 분석 데이터 시뮬레이션 (A360 API에서 가져왔다고 가정하는 데이터)
     */
    private String gatherDeepOperationalData() {
        return String.format("""
                [Current Time: %s]

                1. API_Activity_Logs (Recent 24h):
                   - 'Invoice_Process_KR': Run count 120, Avg Duration 12min, Success 98%%.
                     * Note: Duration spikes to 45min between 13:00~14:00 (Lunch time traffic).
                   - 'HR_Daily_Report': Run count 1, Duration 5min, Status Success.

                2. API_Schedule_Queue:
                   - 'Finance_Close': Scheduled 09:00. Queue Time Avg 18min.
                   - 'Sales_Report': Scheduled 09:00. Queue Time Avg 22min.
                     * Analysis: 09:00 is too crowded.

                3. API_Device_Status:
                   - Total Devices: 5.
                   - Device #01, #02: 95%% Utilization (Overloaded).
                   - Device #03, #04: 10%% Utilization (Idle).
                   - Device #05: Disconnected since 3 days ago.

                4. API_License_Usage:
                   - User 'dev_kim': Assigned 'Creator' license.
                     * Activity: Has not created/edited any bot for 60 days. Only runs bots.
                   - License Pool: 2 Unattended licenses available.
                """, LocalDateTime.now());
    }

    private String refineJsonContent(String content) {
        content = content.replace("```json", "").replace("```", "").trim();
        int start = content.indexOf("[");
        int end = content.lastIndexOf("]");
        if (start != -1 && end != -1) {
            return content.substring(start, end + 1);
        }
        return content;
    }

    private List<AiInsightDto> getDefaultFallbackInsights() {
        List<AiInsightDto> list = new ArrayList<>();
        list.add(AiInsightDto.builder()
                .id(999)
                .type("resource")
                .title("AI 연결 확인 필요")
                .problem("AI 분석 서비스 응답이 지연되고 있습니다.")
                .solution("잠시 후 다시 시도해주세요.")
                .expectedEffect("서비스 안정화")
                .impactScore(50)
                .timeSaveMin(0)
                .build());
        return list;
    }
}