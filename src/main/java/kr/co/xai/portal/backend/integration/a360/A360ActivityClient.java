package kr.co.xai.portal.backend.integration.a360;

import kr.co.xai.portal.backend.integration.a360.dto.*;
import kr.co.xai.portal.backend.integration.a360.service.A360TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class A360ActivityClient {

    private final RestTemplate restTemplate;
    private final A360TokenService a360TokenService;

    @Value("${a360.base-url}")
    private String baseUrl;

    /**
     * 1. 활동 이력 조회 (Activity History)
     */
    public A360ActivityResponse fetchActivities(A360ActivityRequest request) {
        return callApi("/v3/activity/list?historical=true", HttpMethod.POST, request, A360ActivityResponse.class);
    }

    /**
     * [추가됨] AI 분석용: 특정 봇의 최근 로그 조회
     * - fetchActivities를 호출하여 데이터를 가져온 후, 간소화된 Map 리스트로 변환
     */
    public List<Map<String, Object>> fetchRecentLogs(String botName, int days) {
        log.info(">> [A360 Log Fetch Start] Target: {}, Days: {}", (botName == null ? "ALL" : botName), days);

        // 1. 요청 객체 생성
        A360ActivityRequest request = new A360ActivityRequest();

        // 필터 설정 (최근 N일)
        String dateFilterValue = ZonedDateTime.now().minusDays(days)
                .format(DateTimeFormatter.ISO_INSTANT);

        Map<String, Object> filter = new HashMap<>();
        filter.put("operator", "gt");
        filter.put("field", "startDateTime");
        filter.put("value", dateFilterValue);
        request.setFilter(filter);

        // 정렬 설정
        Map<String, Object> sort = new HashMap<>();
        sort.put("field", "startDateTime");
        sort.put("direction", "desc");
        request.setSort(Collections.singletonList(sort));

        // 2. API 호출
        A360ActivityResponse response = fetchActivities(request);

        if (response == null || response.getList() == null) {
            log.warn("<< [A360 Log Fetch] Result is Empty or Null");
            return new ArrayList<>();
        }

        // 3. 데이터 가공 (botName이 있으면 필터링, 없으면 통과)
        List<Map<String, Object>> result = response.getList().stream()
                .filter(item -> {
                    // 봇 이름 필터링 (파라미터가 있을 때만 체크)
                    if (botName == null || botName.isBlank())
                        return true;

                    String name = (String) item.get("activityName");
                    return name != null && name.contains(botName);
                })
                .limit(50) // 전체 조회일 수 있으므로 제한을 좀 늘림
                .map(item -> {
                    Map<String, Object> simpleLog = new HashMap<>();
                    simpleLog.put("activityName", item.get("activityName")); // 봇 이름 식별을 위해 추가
                    simpleLog.put("date", item.get("startDateTime"));

                    Object durationObj = item.get("duration");
                    double durationMin = 0.0;
                    if (durationObj instanceof Number) {
                        durationMin = ((Number) durationObj).doubleValue() / 60.0;
                    }
                    simpleLog.put("duration", String.format("%.1f", durationMin));
                    simpleLog.put("status", item.get("status"));
                    return simpleLog;
                })
                .collect(Collectors.toList());

        log.info("<< [A360 Log Fetch] Retrieved Count: {}", result.size());
        return result;
    }

    /**
     * 2. 스케줄 목록 조회 (Schedules)
     */
    public A360ScheduleResponse fetchSchedules() {
        Map<String, Object> body = new HashMap<>();
        return callApi("/v2/schedule/automations/list", HttpMethod.POST, body, A360ScheduleResponse.class);
    }

    /**
     * 3. 디바이스 목록 조회 (Devices)
     */
    public A360DeviceResponse fetchDevices() {
        Map<String, Object> body = new HashMap<>();
        return callApi("/v2/devices/list", HttpMethod.POST, body, A360DeviceResponse.class);
    }

    /**
     * 4. Execution 상세 조회 (Detail)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchExecutionDetail(String executionId) {
        if (executionId == null || executionId.isBlank())
            return null;
        return callApi("/v1/activity/execution/" + executionId, HttpMethod.GET, null, Map.class);
    }

    /**
     * 공통 API 호출 메서드 (로그 강화)
     */
    private <T> T callApi(String path, HttpMethod method, Object body, Class<T> clazz) {
        String accessToken = a360TokenService.getAccessToken();
        String fullUrl = baseUrl + path;

        // [로그 추가] 실제 호출되는 URL 확인
        log.info(">> [A360 API Request] Method: {}, URL: {}", method, fullUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-authorization", accessToken);

        HttpEntity<Object> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<T> response = restTemplate.exchange(fullUrl, method, entity, clazz);
            log.info("<< [A360 API Response] Code: {}", response.getStatusCode()); // [로그 추가]
            return response.getBody();
        } catch (Exception e) {
            log.error("!! [A360 API Error] path={}, msg={}", path, e.getMessage());
            e.printStackTrace(); // 상세 에러 스택 트레이스 출력
            return null;
        }
    }
}