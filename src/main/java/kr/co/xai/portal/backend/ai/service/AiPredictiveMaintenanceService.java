package kr.co.xai.portal.backend.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.xai.portal.backend.ai.dto.BotRiskDto;
import kr.co.xai.portal.backend.ai.openai.OpenAiClient;
import kr.co.xai.portal.backend.ai.openai.OpenAiRequest;
import kr.co.xai.portal.backend.integration.a360.A360ActivityClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException; // Import 추가

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiPredictiveMaintenanceService {

    private final A360ActivityClient activityClient;
    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<BotRiskDto> analyzeBotRisks() {
        List<BotRiskDto> results = new ArrayList<>();

        log.info(">> Starting Dynamic Bot Analysis...");

        // 1. 최근 7일간 로그 가져오기
        List<Map<String, Object>> allLogs = activityClient.fetchRecentLogs("", 7);

        if (allLogs.isEmpty()) {
            return results;
        }

        // 2. 활성 봇 이름 추출 (최대 3개로 제한하여 토큰 절약)
        Set<String> activeBotNames = allLogs.stream()
                .map(log -> (String) log.get("activityName"))
                .filter(name -> name != null && !name.isBlank())
                .limit(3) // [수정] 과금 방지를 위해 분석 대상을 3개로 제한
                .collect(Collectors.toSet());

        for (String botName : activeBotNames) {
            try {
                // [수정] API 속도 제한(Rate Limit) 회피를 위해 1초 대기
                Thread.sleep(1000);

                List<Map<String, Object>> botLogs = allLogs.stream()
                        .filter(l -> botName.equals(l.get("activityName")))
                        .collect(Collectors.toList());

                BotRiskDto dto = analyzeSingleBot(botName, botLogs);
                if (dto != null) {
                    results.add(dto);
                }
            } catch (Exception e) {
                log.error("Error analyzing bot: " + botName, e);
            }
        }

        return results;
    }

    private BotRiskDto analyzeSingleBot(String botName, List<Map<String, Object>> logs) {
        if (logs.size() < 2)
            return null;

        // 통계 계산
        List<Double> durationHistory = logs.stream()
                .map(m -> {
                    try {
                        return Double.parseDouble(m.get("duration").toString());
                    } catch (Exception e) {
                        return 0.0;
                    }
                })
                .collect(Collectors.toList());

        Collections.reverse(durationHistory);

        double avgDuration = durationHistory.stream().mapToDouble(d -> d).average().orElse(0.0);
        double recentDuration = durationHistory.isEmpty() ? 0.0 : durationHistory.get(durationHistory.size() - 1);

        // [핵심] AI 분석 호출 (오류 처리 포함)
        BotRiskDto aiResult = callOpenAiForAnalysis(botName, logs);

        return BotRiskDto.builder()
                .botName(botName)
                .department("운영팀")
                .avgDuration(Math.round(avgDuration * 10) / 10.0)
                .recentDuration(Math.round(recentDuration * 10) / 10.0)
                .durationHistory(durationHistory)
                .riskScore(aiResult.getRiskScore())
                .status(aiResult.getStatus())
                .predictedFailure(aiResult.getPredictedFailure())
                .analysisReport(aiResult.getAnalysisReport())
                .build();
    }

    private BotRiskDto callOpenAiForAnalysis(String botName, List<Map<String, Object>> logs) {
        try {
            // [수정] 토큰 절약을 위해 로그 샘플링 개수를 5개로 축소
            int limit = Math.min(logs.size(), 5);
            StringBuilder logSummary = new StringBuilder();
            for (int i = 0; i < limit; i++) {
                Map<String, Object> log = logs.get(i);
                logSummary.append(String.format("- D:%s, T:%s m, S:%s\n",
                        log.get("date"), log.get("duration"), log.get("status")));
            }

            String prompt = "Analyze bot '" + botName + "' logs.\n" +
                    "[Logs]\n" + logSummary + "\n" +
                    "Return JSON: { \"riskScore\": (0-100), \"status\": \"(CRITICAL|WARNING|NORMAL)\", " +
                    "\"predictedFailure\": \"(Korean short prediction)\", " +
                    "\"analysisReport\": \"(Korean summary)\" }";

            OpenAiRequest request = new OpenAiRequest();
            request.setModel("gpt-4o-mini"); // [수정] gpt-4o -> gpt-4o-mini (비용 절감)
            request.addMessage("user", prompt);

            String responseJson = openAiClient.call(request);
            responseJson = cleanJson(responseJson);

            JsonNode root = objectMapper.readTree(responseJson);

            return BotRiskDto.builder()
                    .riskScore(root.path("riskScore").asDouble(0.0))
                    .status(root.path("status").asText("NORMAL"))
                    .predictedFailure(root.path("predictedFailure").asText("-"))
                    .analysisReport(root.path("analysisReport").asText("특이사항 없음"))
                    .build();

        } catch (HttpClientErrorException.TooManyRequests e) {
            // [추가] 429 오류(사용량 초과) 별도 처리
            log.warn("OpenAI Rate Limit Exceeded for bot: {}", botName);
            return BotRiskDto.builder()
                    .riskScore(0.0)
                    .status("NORMAL")
                    .predictedFailure("분석 지연")
                    .analysisReport("AI 사용량이 많아 분석을 건너뛰었습니다. (잠시 후 다시 시도)")
                    .build();

        } catch (Exception e) {
            log.error("AI Analysis Failed", e);
            return BotRiskDto.builder()
                    .riskScore(0.0)
                    .status("NORMAL")
                    .predictedFailure("분석 실패")
                    .analysisReport("AI 서버 연결 실패")
                    .build();
        }
    }

    private String cleanJson(String text) {
        if (text == null)
            return "{}";
        text = text.trim();
        if (text.startsWith("```json"))
            text = text.substring(7);
        if (text.startsWith("```"))
            text = text.substring(3);
        if (text.endsWith("```"))
            text = text.substring(0, text.length() - 3);
        return text.trim();
    }
}