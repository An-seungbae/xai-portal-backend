package kr.co.xai.portal.backend.security;

import kr.co.xai.portal.backend.service.PortalUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * PasswordEncoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * DaoAuthenticationProvider
     */
    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider(
            PortalUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {

        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    /**
     * AuthenticationManager
     */
    @Bean
    public AuthenticationManager authenticationManager(
            DaoAuthenticationProvider daoAuthenticationProvider) {

        return new ProviderManager(daoAuthenticationProvider);
    }

    /**
     * JWT Filter
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(
            JwtTokenProvider tokenProvider) {

        return new JwtAuthenticationFilter(tokenProvider);
    }

    /**
     * Security Filter Chain
     */
    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            CorsConfigurationSource corsConfigurationSource) throws Exception {

        http
                // ✅ CORS를 Security에 "명시적으로" 바인딩
                .cors(cors -> cors.configurationSource(corsConfigurationSource))

                // CSRF 비활성화 (JWT)
                .csrf(csrf -> csrf.disable())

                // 세션 미사용
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 접근 제어
                .authorizeHttpRequests(auth -> auth

                        // ✅ Preflight 요청은 무조건 허용
                        .antMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // 인증 API
                        .antMatchers("/api/auth/**").permitAll()
                        .antMatchers("/api/health/**").permitAll()

                        // 보호 API
                        .antMatchers("/api/errors/**").authenticated()
                        .antMatchers("/api/ai/**").authenticated()

                        .anyRequest().authenticated())

                // JWT 필터
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
