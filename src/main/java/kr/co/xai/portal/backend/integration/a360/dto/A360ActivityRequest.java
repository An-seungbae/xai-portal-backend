package kr.co.xai.portal.backend.integration.a360.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor // 추가!
@AllArgsConstructor // 추가 (선택사항)
public class A360ActivityRequest {

    private List<String> fields;
    private Map<String, Object> filter;
    private List<Map<String, Object>> sort;
    private Page page;

    @Getter
    @Setter
    @NoArgsConstructor // 내부 클래스에도 추가!
    @AllArgsConstructor // 추가 (선택사항)
    public static class Page {
        private int offset;
        private int length;
    }
}