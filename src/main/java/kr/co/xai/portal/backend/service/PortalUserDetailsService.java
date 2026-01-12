package kr.co.xai.portal.backend.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PortalUserDetailsService implements UserDetailsService {

    private final JdbcTemplate jdbcTemplate;

    public PortalUserDetailsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        // 로그인 기준이 email이므로 email로 조회하도록 수정
        String sql = "SELECT email, password_hash, roles, enabled " +
                "FROM portal_user " +
                "WHERE email = ?";

        List<UserDetails> found = jdbcTemplate.query(sql, rs -> {
            if (!rs.next()) {
                return List.of();
            }

            String u = rs.getString("email");
            String pw = rs.getString("password_hash");
            String roles = rs.getString("roles");
            boolean enabled = rs.getBoolean("enabled");

            List<SimpleGrantedAuthority> authorities = Arrays.stream(roles.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());

            return List.of(User.withUsername(u)
                    .password(pw)
                    .authorities(authorities)
                    .disabled(!enabled)
                    .build());
        }, email);

        if (found.isEmpty()) {
            throw new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + email);
        }

        return found.get(0);
    }
}