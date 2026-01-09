package kr.co.xai.portal.backend.integration.a360.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class A360DeviceResponse {

    private Map<String, Object> page;
    private List<DeviceItem> list;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DeviceItem {
        private String id;

        @JsonAlias("hostName")
        private String hostName;

        private String status; // CONNECTED, DISCONNECTED

        @JsonAlias("botAgentVersion")
        private String botAgentVersion;

        // Service 코드에서 item.getUserName()을 호출하므로 맞춤
        // API의 'nickname' 또는 'username' 필드 매핑
        @JsonAlias({ "nickname", "username", "userName" })
        private String userName;

        private String type; // SINGLE_USER etc
    }
}