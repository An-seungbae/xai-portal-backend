package kr.co.xai.portal.backend.integration.a360.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ConfigurationProperties(prefix = "a360")
public class A360Properties {

    /**
     * 예) http://a360.bitekic.co.kr
     */
    private String baseUrl;

    /**
     * 예) /v2/authentication
     */
    private String authenticationPath = "/v2/authentication";

    /**
     * A360 관리자 계정
     */
    private String username;

    /**
     * A360 관리자 비밀번호
     */
    private String password;

    /**
     * 토큰 만료 전 미리 재발급(초)
     * 예: 60이면 만료 60초 전부터 새 토큰 발급
     */
    private long refreshSkewSeconds = 60;

    /**
     * 인증 응답에 expiresIn(초)가 없을 때 사용할 기본 만료(초)
     * 예: 3600
     */
    private long defaultExpiresInSeconds = 3600;
}
