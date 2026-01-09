package kr.co.xai.portal.backend.integration.a360.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true) // 모르는 필드가 있어도 에러 방지
public class A360ScheduleResponse {

    private Map<String, Object> page;
    private List<ScheduleItem> list;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ScheduleItem {
        private String id;

        // Service 코드에서 item.getName()을 호출하므로 변수명을 name으로 맞춤
        // 실제 API의 'activityName' 필드를 매핑
        @JsonAlias({ "activityName", "name", "automationName" })
        private String name;

        private String taskType;

        private String scheduleType; // WEEKLY, DAILY etc

        private String status; // ACTIVE, INACTIVE

        // Service 코드에서 item.getNextExecution()을 호출하므로 맞춤
        @JsonAlias({ "nextRunDateTime", "nextExecutionStart", "nextExecution" })
        private String nextExecution;

        private String startDate;
        private String startTime;
    }
}