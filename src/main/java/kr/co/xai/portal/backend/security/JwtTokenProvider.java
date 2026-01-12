package kr.co.xai.portal.backend.security;

import kr.co.xai.portal.backend.config.JwtProperties;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.SignatureAlgorithm;

import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Component
public class JwtTokenProvider {

    private final JwtProperties props;
    private final Key key;

    public JwtTokenProvider(JwtProperties props) {
        this.props = props;

        if (props.getSecret() == null || props.getSecret().length() < 32) {
            throw new IllegalStateException("jwt.secret 는 최소 32자 이상이어야 합니다.");
        }

        this.key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * subject = email 로 발급
     */
    public String createAccessToken(String email, List<String> roles) {
        long validityMs = props.getAccessTokenValiditySeconds() * 1000L;
        Date now = new Date();
        Date exp = new Date(now.getTime() + validityMs);

        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(now)
                .setExpiration(exp)
                .addClaims(Map.of("roles", roles))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean validate(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
