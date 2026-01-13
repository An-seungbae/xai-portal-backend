package kr.co.xai.portal.backend.integration.a360;

import kr.co.xai.portal.backend.integration.a360.dto.*;
import kr.co.xai.portal.backend.integration.a360.service.A360TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
     * 2. [신규] 진행중인 활동 (Unknown/Active Jobs) 조회
     * GET /v1/activity/unknownjobs
     */
    public List<Map<String, Object>> fetchUnknownJobs() {
        // 이 API는 보통 List 형태로 반환됨
        return callApiList("/v1/activity/unknownjobs", HttpMethod.GET, null);
    }

    /**
     * [AI 분석용] 특정 봇의 최근 로그 조회 (데이터 누락 방지 로직 적용)
     */
    public List<Map<String, Object>> fetchRecentLogs(String botName, int days) {
        log.info(">> [A360 Log Fetch] Target: {}, Days: {}", (botName == null || botName.isBlank() ? "ALL" : botName),
                days);

        A360ActivityRequest request = new A360ActivityRequest();

        // 1. 날짜 필터 (최근 N일)
        String dateFilterValue = ZonedDateTime.now().minusDays(days)
                .format(DateTimeFormatter.ISO_INSTANT);

        Map<String, Object> filter = new HashMap<>();
        filter.put("operator", "gt");
        filter.put("field", "startDateTime");
        filter.put("value", dateFilterValue);
        request.setFilter(filter);

        // 2. 정렬 (최신순)
        Map<String, Object> sort = new HashMap<>();
        sort.put("field", "startDateTime");
        sort.put("direction", "desc");

        // [수정] Map을 List에 담아서 설정 (Map에는 add 메소드가 없음)
        request.setSort(Collections.singletonList(sort));

        // 3. 페이징 (충분한 크기 확보)
        A360ActivityRequest.Page page = new A360ActivityRequest.Page();
        page.setOffset(0);
        page.setLength(1000);
        request.setPage(page);

        // API 호출
        A360ActivityResponse response = fetchActivities(request);

        if (response == null || response.getList() == null) {
            log.warn("<< [A360 Log Fetch] Result is Empty");
            return new ArrayList<>();
        }

        // 데이터 필터링 및 가공
        List<Map<String, Object>> result = response.getList().stream()
                .filter(item -> {
                    if (botName == null || botName.isBlank())
                        return true;
                    String name = (String) item.get("activityName");
                    return name != null && name.contains(botName);
                })
                .map(item -> {
                    Map<String, Object> simpleLog = new HashMap<>();
                    simpleLog.put("activityName", item.get("activityName"));
                    simpleLog.put("date", item.get("startDateTime"));

                    Object durationObj = item.get("duration");
                    double durationMin = 0.0;
                    if (durationObj instanceof Number) {
                        // 초 단위 가정 -> 분 단위 변환
                        durationMin = ((Number) durationObj).doubleValue() / 60.0;
                    }
                    simpleLog.put("duration", String.format("%.1f", durationMin));
                    simpleLog.put("status", item.get("status"));
                    return simpleLog;
                })
                .collect(Collectors.toList());

        log.info("<< [A360 Log Fetch] Count: {}", result.size());
        return result;
    }

    /**
     * 3. 스케줄 목록 조회 (데이터 누락 방지 로직 적용)
     */
    public A360ScheduleResponse fetchSchedules() {
        Map<String, Object> body = new HashMap<>();

        // [필수] 필터 및 페이징 정보가 있어야 전체 목록이 나옴
        body.put("filter", new HashMap<>());
        body.put("sort", new ArrayList<>());

        Map<String, Integer> page = new HashMap<>();
        page.put("offset", 0);
        page.put("length", 1000); // 넉넉하게
        body.put("page", page);

        return callApi("/v2/schedule/automations/list", HttpMethod.POST, body, A360ScheduleResponse.class);
    }

    /**
     * 4. 디바이스 목록 조회
     */
    public A360DeviceResponse fetchDevices() {
        Map<String, Object> body = new HashMap<>();
        body.put("filter", new HashMap<>());

        Map<String, Integer> page = new HashMap<>();
        page.put("offset", 0);
        page.put("length", 1000);
        body.put("page", page);

        return callApi("/v2/devices/list", HttpMethod.POST, body, A360DeviceResponse.class);
    }

    /**
     * 5. 상세 조회
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchExecutionDetail(String executionId) {
        if (executionId == null || executionId.isBlank())
            return null;
        return callApi("/v1/activity/execution/" + executionId, HttpMethod.GET, null, Map.class);
    }

    /**
     * 6. 라이선스 정보 조회 (Licenses) - [신규 추가]
     * GET /v2/license
     */
    public A360LicenseResponse fetchLicenses() {
        return callApi("/v2/license", HttpMethod.GET, null, A360LicenseResponse.class);
    }

    // --- Private Methods ---

    private <T> T callApi(String path, HttpMethod method, Object body, Class<T> clazz) {
        String accessToken = a360TokenService.getAccessToken();
        String fullUrl = baseUrl + path;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-authorization", accessToken);

        HttpEntity<Object> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<T> response = restTemplate.exchange(fullUrl, method, entity, clazz);
            return response.getBody();
        } catch (Exception e) {
            log.error("!! [A360 API Error] path={}, msg={}", path, e.getMessage());
            return null;
        }
    }

    private List<Map<String, Object>> callApiList(String path, HttpMethod method, Object body) {
        String accessToken = a360TokenService.getAccessToken();
        String fullUrl = baseUrl + path;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-authorization", accessToken);

        HttpEntity<Object> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    fullUrl,
                    method,
                    entity,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {
                    });
            return response.getBody();
        } catch (Exception e) {
            log.error("!! [A360 API List Error] path={}, msg={}", path, e.getMessage());
            return new ArrayList<>();
        }
    }
}