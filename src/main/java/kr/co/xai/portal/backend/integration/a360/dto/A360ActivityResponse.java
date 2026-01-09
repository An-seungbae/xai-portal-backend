package kr.co.xai.portal.backend.integration.a360.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor // 👈 추가!
@AllArgsConstructor // 👈 추가 (선택사항)
public class A360ActivityResponse {

    private int total;
    private List<Map<String, Object>> list;
}