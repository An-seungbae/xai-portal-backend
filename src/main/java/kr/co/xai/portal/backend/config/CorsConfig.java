package kr.co.xai.portal.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();

        // [찰스 수정] 주인님의 모든 접속 경로를 강제로 허용합니다.
        cfg.setAllowedOrigins(List.of(
                "http://localhost", // 빌드된 프론트엔드 접속 주소 (필수!)
                "http://localhost:3000", // React 개발 포트
                "http://localhost:5173", // Vite 개발 포트
                "http://localhost:8080", // 백엔드 직접 호출
                "http://192.168.3.54:3000" // 외부 IP
        ));

        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        cfg.setExposedHeaders(List.of("Authorization"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}