package kr.co.xai.portal.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 설정 바인딩 전용 클래스
 * 
 * @Configuration 절대 사용 금지
 */
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String secret;
    private long accessTokenValiditySeconds = 3600;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getAccessTokenValiditySeconds() {
        return accessTokenValiditySeconds;
    }

    public void setAccessTokenValiditySeconds(long accessTokenValiditySeconds) {
        this.accessTokenValiditySeconds = accessTokenValiditySeconds;
    }
}
