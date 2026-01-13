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

        // [찰스의 수정] 설정 파일 무시하고 직접 리스트를 주입합니다.
        // 주인님의 프론트엔드 주소(http://localhost)가 포함되어 있습니다.
        List<String> explicitOrigins = List.of(
                "http://localhost", // 현재 접속 주소 (필수)
                "http://localhost:3000", // React/Vue 기본 포트
                "http://localhost:5173", // Vite 기본 포트
                "http://localhost:8080", // 백엔드 포트
                "http://192.168.3.54:3000" // 외부 IP 접속
        );

        cfg.setAllowedOrigins(explicitOrigins);

        // 모든 HTTP 메소드 허용
        cfg.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // 모든 헤더 허용
        cfg.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "X-Requested-With"));

        // 응답 헤더 노출 (Authorization 등)
        cfg.setExposedHeaders(List.of("Authorization"));

        // 자격 증명(쿠키/토큰) 허용
        cfg.setAllowCredentials(true);

        // Preflight 캐시 시간 설정 (1시간)
        cfg.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);

        return source;
    }
}