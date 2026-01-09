package kr.co.xai.portal.backend.security;

import kr.co.xai.portal.backend.service.PortalUserDetailsService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.http.HttpMethod;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * ✅ PasswordEncoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * ✅ DaoAuthenticationProvider
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
     * ✅ AuthenticationManager
     */
    @Bean
    public AuthenticationManager authenticationManager(
            DaoAuthenticationProvider daoAuthenticationProvider) {

        return new ProviderManager(daoAuthenticationProvider);
    }

    /**
     * ✅ JWT Filter Bean (🔥 핵심)
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(
            JwtTokenProvider tokenProvider) {

        return new JwtAuthenticationFilter(tokenProvider);
    }

    /**
     * ✅ Security Filter Chain
     */
    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {

        http
                // CSRF 비활성화 (JWT)
                .csrf().disable()

                // CORS 허용
                .cors().and()

                // 세션 미사용
                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()

                // 접근 제어
                .authorizeRequests()

                // Preflight
                .antMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // 인증 / 헬스
                .antMatchers("/api/auth/**").permitAll()
                .antMatchers("/api/health/**").permitAll()

                // 오류관리 API (로그인 사용자)
                .antMatchers("/api/errors/**").authenticated()
                .antMatchers("/api/ai/**").authenticated()

                // 그 외
                .anyRequest().authenticated()
                .and()

                // ✅ Bean으로 등록된 JWT 필터 사용
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
