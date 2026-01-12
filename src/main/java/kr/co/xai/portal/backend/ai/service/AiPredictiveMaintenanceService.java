package kr.co.xai.portal.backend.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.xai.portal.backend.ai.dto.BotRiskDto;
import kr.co.xai.portal.backend.ai.openai.OpenAiClient;
import kr.co.xai.portal.backend.ai.openai.OpenAiRequest;
import kr.co.xai.portal.backend.integration.a360.A360ActivityClient;
import kr.co.xai.portal.backend.integration.a360.dto.A360ScheduleResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

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

        log.info(">> Starting AI Predictive Maintenance (Ongoing + History)...");

        // 1. [Context] 스케줄 정보 미리 조회
        Map<String, A360ScheduleResponse.ScheduleItem> scheduleMap = new HashMap<>();
        try {
            A360ScheduleResponse scheduleRes = activityClient.fetchSchedules();
            if (scheduleRes != null && scheduleRes.getList() != null) {
                for (A360ScheduleResponse.ScheduleItem item : scheduleRes.getList()) {
                    if (item.getName() != null)
                        scheduleMap.put(item.getName(), item);
                }
            }
        } catch (Exception e) {
            log.warn(">> Failed to fetch schedules.", e);
        }

        // 2. [Target 1] 현재 진행 중인 봇 조회 (우선 분석 대상)
        List<Map<String, Object>> ongoingJobs = activityClient.fetchUnknownJobs();
        Set<String> ongoingBotNames = ongoingJobs.stream()
                .map(j -> (String) j.get("automationName"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 3. [History] 최근 14일간 로그 조회 (통계 데이터용)
        List<Map<String, Object>> allLogs = activityClient.fetchRecentLogs("", 14);
        Map<String, List<Map<String, Object>>> historyMap = allLogs.stream()
                .filter(l -> l.get("activityName") != null)
                .collect(Collectors.groupingBy(l -> (String) l.get("activityName")));

        // 4. 분석 대상 선정 (진행 중인 봇 + 최근 활동 봇 중 상위 5개)
        Set<String> targetBots = new HashSet<>(ongoingBotNames);
        targetBots.addAll(historyMap.keySet().stream().limit(5).collect(Collectors.toList()));

        // 5. 봇별 분석 수행
        for (String botName : targetBots) {
            try {
                // API Rate Limit 조절
                Thread.sleep(300);

                List<Map<String, Object>> botHistory = historyMap.getOrDefault(botName, new ArrayList<>());
                A360ScheduleResponse.ScheduleItem schedule = scheduleMap.get(botName);
                boolean isRunning = ongoingBotNames.contains(botName);

                BotRiskDto dto = analyzeSingleBot(botName, botHistory, schedule, isRunning);
                if (dto != null) {
                    results.add(dto);
                }
            } catch (Exception e) {
                log.error("Error analyzing bot: " + botName, e);
            }
        }

        // 중요도 순 정렬 (Running > Critical > Warning ...)
        results.sort((a, b) -> {
            if (a.getStatus().equals("CRITICAL") && !b.getStatus().equals("CRITICAL"))
                return -1;
            if (b.getStatus().equals("CRITICAL") && !a.getStatus().equals("CRITICAL"))
                return 1;
            return Double.compare(b.getRiskScore(), a.getRiskScore());
        });

        return results;
    }

    private BotRiskDto analyzeSingleBot(String botName, List<Map<String, Object>> history,
            A360ScheduleResponse.ScheduleItem schedule, boolean isRunning) {

        // 데이터가 너무 없으면 분석 스킵 (단, 실행 중이면 분석 시도)
        if (history.size() < 2 && !isRunning)
            return null;

        // --- 1. 정량적 통계 계산 (Statistics) ---
        int totalRuns = history.size();
        int failCount = 0;
        List<Double> durationHistory = new ArrayList<>();

        for (Map<String, Object> log : history) {
            String status = (String) log.get("status");
            if (status != null && (status.contains("FAILED") || status.contains("OUT") || status.contains("ABORTED"))) {
                failCount++;
            }
            try {
                durationHistory.add(Double.parseDouble(log.get("duration").toString()));
            } catch (Exception e) {
                durationHistory.add(0.0);
            }
        }
        Collections.reverse(durationHistory); // 그래프용 (과거 -> 현재)

        double failureRate = (totalRuns > 0) ? ((double) failCount / totalRuns) * 100.0 : 0.0;
        double avgDuration = durationHistory.stream().mapToDouble(d -> d).average().orElse(0.0);
        double recentDuration = durationHistory.isEmpty() ? 0.0 : durationHistory.get(durationHistory.size() - 1);

        // --- 2. AI 분석 요청 (Prediction) ---
        BotRiskDto aiResult = callOpenAiForPrediction(botName, history, schedule, isRunning, failureRate, totalRuns);

        return BotRiskDto.builder()
                .botName(botName)
                .department(isRunning ? "🚀 Running Now" : (schedule != null ? "Scheduled" : "Manual"))
                .avgDuration(Math.round(avgDuration * 10) / 10.0)
                .recentDuration(Math.round(recentDuration * 10) / 10.0)
                .durationHistory(durationHistory)
                .riskScore(aiResult.getRiskScore())
                .status(aiResult.getStatus())
                .predictedFailure(aiResult.getPredictedFailure())
                .analysisReport(aiResult.getAnalysisReport())
                .build();
    }

    private BotRiskDto callOpenAiForPrediction(String botName, List<Map<String, Object>> history,
            A360ScheduleResponse.ScheduleItem schedule,
            boolean isRunning, double failureRate, int totalRuns) {
        try {
            // Context Building
            String runStatus = isRunning ? "Currently RUNNING (Active Job)" : "Idle / Scheduled";
            String scheduleInfo = (schedule != null)
                    ? String.format("Type: %s, Next: %s", schedule.getScheduleType(), schedule.getNextExecution())
                    : "No Schedule";

            // Recent Logs Summary
            StringBuilder logSummary = new StringBuilder();
            history.stream().limit(5).forEach(l -> logSummary.append(
                    String.format("- [%s] %s (Time: %s m)\n", l.get("date"), l.get("status"), l.get("duration"))));

            // Prompt Engineering
            String prompt = String.format(
                    "Analyze the risk for RPA Bot '%s'.\n\n" +
                            "[Current Status] %s\n" +
                            "[History Stats] Total: %d, Fail Rate: %.1f%%\n" +
                            "[Schedule] %s\n" +
                            "[Recent Logs]\n%s\n\n" +
                            "Task:\n" +
                            "1. If 'Currently RUNNING' and History Fail Rate is high (>20%%), predict 'HIGH RISK' for this run.\n"
                            +
                            "2. If 'Idle' but failures match schedule pattern, predict future risk.\n" +
                            "3. Return JSON ONLY.\n\n" +
                            "JSON Format:\n" +
                            "{\n" +
                            "  \"riskScore\": (0-100),\n" +
                            "  \"status\": \"(CRITICAL|WARNING|NORMAL)\",\n" +
                            "  \"predictedFailure\": \"(Short prediction in Korean, e.g. '현재 실행 중 - 실패 확률 높음')\",\n" +
                            "  \"analysisReport\": \"(Insight in Korean)\"\n" +
                            "}",
                    botName, runStatus, totalRuns, failureRate, scheduleInfo, logSummary.toString());

            OpenAiRequest request = new OpenAiRequest();
            request.setModel("gpt-4o-mini");
            // [수정됨] 토큰 제한 설정 필수 (OpenAI API 요구사항)
            request.setMaxTokens(2000);
            request.addMessage("user", prompt);

            String responseJson = openAiClient.call(request);
            JsonNode root = objectMapper.readTree(cleanJson(responseJson));

            return BotRiskDto.builder()
                    .riskScore(root.path("riskScore").asDouble(0.0))
                    .status(root.path("status").asText("NORMAL"))
                    .predictedFailure(root.path("predictedFailure").asText("-"))
                    .analysisReport(root.path("analysisReport").asText("특이사항 없음"))
                    .build();

        } catch (Exception e) {
            log.error("AI Prediction Failed", e);
            // 에러 시 Fallback (기계적 룰 기반)
            double fallbackScore = isRunning && failureRate > 20 ? 80.0 : failureRate;
            String fallbackStatus = fallbackScore > 50 ? "WARNING" : "NORMAL";
            return BotRiskDto.builder()
                    .riskScore(fallbackScore)
                    .status(fallbackStatus)
                    .predictedFailure(isRunning ? "AI 분석 불가 (실행 중)" : "-")
                    .analysisReport("AI 연결 지연. 히스토리 통계를 참고하세요.")
                    .build();
        }
    }

    private String cleanJson(String text) {
        if (text == null)
            return "{}";
        return text.replaceAll("```json", "").replaceAll("```", "").trim();
    }
}