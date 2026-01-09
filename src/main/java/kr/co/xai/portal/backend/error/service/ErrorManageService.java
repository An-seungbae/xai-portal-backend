package kr.co.xai.portal.backend.error.service;

import kr.co.xai.portal.backend.error.dto.PortalErrorDto;
import kr.co.xai.portal.backend.integration.a360.A360ActivityClient;
import kr.co.xai.portal.backend.integration.a360.dto.A360ActivityRequest;
import kr.co.xai.portal.backend.integration.a360.dto.A360ActivityResponse;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ErrorManageService {

        private final A360ActivityClient a360Client;

        /**
         * =========================
         * 오류 목록 조회 (기존)
         * =========================
         */
        public Map<String, Object> getErrors(String keyword, int page, int size) {

                A360ActivityRequest request = buildRequest(keyword, page, size);
                A360ActivityResponse response = a360Client.fetchActivities(request);

                List<PortalErrorDto> items = response.getList().stream()
                                .map(this::toPortalDto)
                                .collect(Collectors.toList());

                return Map.of(
                                "total", response.getTotal(),
                                "items", items);
        }

        /**
         * =========================
         * 🔹 A360 Execution 상세 조회 (신규)
         * =========================
         *
         * 목록 → 상세 이동 시 사용
         */
        public Map<String, Object> getExecutionDetail(String executionId) {

                if (executionId == null || executionId.isBlank()) {
                        throw new IllegalArgumentException("executionId is required");
                }

                return a360Client.fetchExecutionDetail(executionId);
        }

        // =================================================
        // 내부 메서드 (목록 요청/매핑 전용)
        // =================================================

        private A360ActivityRequest buildRequest(String keyword, int page, int size) {

                Map<String, Object> statusFilter = Map.of(
                                "operator", "or",
                                "operands", List.of(
                                                Map.of("field", "status", "value", "RUN_FAILED", "operator", "eq"),
                                                Map.of("field", "status", "value", "RUN_TIMED_OUT", "operator", "eq"),
                                                Map.of("field", "status", "value", "DEPLOY_FAILED", "operator", "eq")));

                Map<String, Object> nameFilter = Map.of(
                                "field", "automationName",
                                "value", keyword == null ? "" : keyword,
                                "operator", "substring");

                Map<String, Object> filter = Map.of(
                                "operator", "and",
                                "operands", List.of(
                                                Map.of(
                                                                "operator", "and",
                                                                "operands", List.of(statusFilter, nameFilter))));

                A360ActivityRequest.Page pageObj = new A360ActivityRequest.Page();
                pageObj.setOffset((page - 1) * size);
                pageObj.setLength(size);

                A360ActivityRequest req = new A360ActivityRequest();
                req.setFields(List.of());
                req.setFilter(filter);
                req.setSort(List.of(
                                Map.of("field", "endDateTime", "direction", "desc")));
                req.setPage(pageObj);

                return req;
        }

        @SuppressWarnings("unchecked")
        private PortalErrorDto toPortalDto(Map<String, Object> raw) {

                PortalErrorDto dto = new PortalErrorDto();

                // ===== 기존 필드 =====
                dto.setBotName(resolveBotName(raw));
                dto.setErrorCode(resolveErrorCode(raw));
                dto.setMessage(resolveErrorMessage(raw));
                dto.setOccurredAt(Objects.toString(raw.get("endDateTime"), ""));

                // ===== A360 확장 필드 =====
                dto.setActivityId(Objects.toString(raw.get("id"), ""));
                dto.setStatus(Objects.toString(raw.get("status"), ""));
                dto.setAutomationType(Objects.toString(raw.get("activityType"), ""));
                dto.setActivityName(Objects.toString(raw.get("automationName"), ""));
                dto.setPriority(Objects.toString(raw.get("automationPriority"), ""));
                dto.setDeviceName(Objects.toString(raw.get("deviceName"), ""));
                dto.setUserName(Objects.toString(raw.get("userName"), ""));
                dto.setStartedOn(Objects.toString(raw.get("startDateTime"), ""));
                dto.setEndedOn(Objects.toString(raw.get("endDateTime"), ""));

                return dto;
        }

        private String resolveBotName(Map<String, Object> raw) {

                String currentBotName = Objects.toString(raw.get("currentBotName"), "");
                if (!currentBotName.isEmpty()) {
                        return currentBotName;
                }
                return Objects.toString(raw.get("automationName"), "");
        }

        @SuppressWarnings("unchecked")
        private String resolveErrorMessage(Map<String, Object> raw) {

                Object errorObj = raw.get("error");
                if (errorObj instanceof Map) {
                        Map<String, Object> error = (Map<String, Object>) errorObj;
                        return Objects.toString(error.get("message"), "");
                }
                return "";
        }

        @SuppressWarnings("unchecked")
        private String resolveErrorCode(Map<String, Object> raw) {

                Object errorObj = raw.get("error");
                if (errorObj instanceof Map) {
                        Map<String, Object> error = (Map<String, Object>) errorObj;
                        return Objects.toString(error.get("code"), "");
                }
                return "";
        }
}
