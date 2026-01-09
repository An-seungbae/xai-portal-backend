package kr.co.xai.portal.backend.integration.a360.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.xai.portal.backend.integration.a360.config.A360Properties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

@Service
@RequiredArgsConstructor
public class A360TokenService {

    private final A360Properties props;
    private final ObjectMapper objectMapper;

    private final ReentrantLock lock = new ReentrantLock();

    // 캐시
    private volatile String cachedToken;
    private volatile Instant cachedExpireAt;

    /**
     * A360 API 호출 시 사용할 Bearer 토큰 반환 (없거나 만료 임박 시 자동 재발급)
     */
    public String getAccessToken() {
        // 1) 캐시가 유효하면 즉시 반환
        if (isTokenValid()) {
            return cachedToken;
        }

        // 2) 동시성 대비: 한 번만 재발급
        lock.lock();
        try {
            if (isTokenValid()) {
                return cachedToken;
            }
            issueNewToken();
            return cachedToken;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 강제 재발급 (운영/장애 대응용)
     */
    public String refreshNow() {
        lock.lock();
        try {
            issueNewToken();
            return cachedToken;
        } finally {
            lock.unlock();
        }
    }

    private boolean isTokenValid() {
        if (!StringUtils.hasText(cachedToken) || cachedExpireAt == null) {
            return false;
        }
        long skew = Math.max(0, props.getRefreshSkewSeconds());
        Instant now = Instant.now();
        // 만료 임박이면 무효 처리
        return now.isBefore(cachedExpireAt.minusSeconds(skew));
    }

    private void issueNewToken() {
        validateProps();

        String url = normalizeUrl(props.getBaseUrl(), props.getAuthenticationPath());

        Map<String, Object> body = new HashMap<>();
        body.put("username", props.getUsername());
        body.put("password", props.getPassword());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(MediaType.parseMediaTypes(MediaType.APPLICATION_JSON_VALUE));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        RestTemplate rt = new RestTemplateBuilder()
                .setConnectTimeout(java.time.Duration.ofSeconds(10))
                .setReadTimeout(java.time.Duration.ofSeconds(30))
                .build();

        ResponseEntity<String> resp;
        try {
            resp = rt.exchange(url, HttpMethod.POST, entity, String.class);
        } catch (RestClientException e) {
            throw new IllegalStateException("A360 인증 API 호출 실패: " + e.getMessage(), e);
        }

        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException(
                    "A360 인증 실패: HTTP " + resp.getStatusCodeValue() + " / body=" + safeBody(resp.getBody()));
        }

        String respBody = resp.getBody();
        if (!StringUtils.hasText(respBody)) {
            throw new IllegalStateException("A360 인증 응답 body가 비어있습니다.");
        }

        parseAndCache(respBody);
    }

    private void parseAndCache(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);

            // A360 응답 토큰 필드명은 환경/버전에 따라 다를 수 있어 후보를 넉넉히 둠
            String token = firstText(root,
                    "token",
                    "accessToken",
                    "access_token",
                    "jwt",
                    "authToken",
                    "authenticationToken");

            if (!StringUtils.hasText(token)) {
                throw new IllegalStateException("A360 인증 응답에서 토큰 필드를 찾지 못했습니다. 응답=" + shrink(json));
            }

            // expiresIn(초) 후보
            Long expiresIn = firstLong(root,
                    "expiresIn",
                    "expires_in",
                    "expires",
                    "expiry",
                    "expiration");

            long expSec = (expiresIn != null && expiresIn > 0)
                    ? expiresIn
                    : Math.max(60, props.getDefaultExpiresInSeconds());

            this.cachedToken = token;
            this.cachedExpireAt = Instant.now().plusSeconds(expSec);

        } catch (Exception e) {
            throw new IllegalStateException("A360 인증 응답 파싱 실패: " + e.getMessage(), e);
        }
    }

    private String firstText(JsonNode root, String... keys) {
        for (String k : keys) {
            JsonNode n = root.get(k);
            if (n != null && n.isTextual()) {
                String v = n.asText();
                if (StringUtils.hasText(v))
                    return v;
            }
        }
        return null;
    }

    private Long firstLong(JsonNode root, String... keys) {
        for (String k : keys) {
            JsonNode n = root.get(k);
            if (n == null)
                continue;
            if (n.isNumber())
                return n.asLong();
            if (n.isTextual()) {
                try {
                    return Long.parseLong(n.asText().trim());
                } catch (NumberFormatException ignore) {
                }
            }
        }
        return null;
    }

    private void validateProps() {
        if (!StringUtils.hasText(props.getBaseUrl())) {
            throw new IllegalStateException("a360.base-url 이 비어있습니다.");
        }
        if (!StringUtils.hasText(props.getUsername())) {
            throw new IllegalStateException("a360.username 이 비어있습니다.");
        }
        if (!StringUtils.hasText(props.getPassword())) {
            throw new IllegalStateException("a360.password 이 비어있습니다.");
        }
    }

    private String normalizeUrl(String baseUrl, String path) {
        String b = baseUrl.trim();
        String p = (path == null) ? "" : path.trim();

        if (b.endsWith("/"))
            b = b.substring(0, b.length() - 1);
        if (!p.startsWith("/"))
            p = "/" + p;

        return b + p;
    }

    private String safeBody(String body) {
        if (!StringUtils.hasText(body))
            return "";
        return shrink(body);
    }

    private String shrink(String s) {
        s = s.replace("\r", " ").replace("\n", " ").trim();
        if (s.length() > 500)
            return s.substring(0, 500) + "...";
        return s;
    }
}
