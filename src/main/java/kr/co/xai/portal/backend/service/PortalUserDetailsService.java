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

    /**
     * 기본 테이블/컬럼 가정:
     * portal_user(username, password_hash, roles, enabled)
     * roles 예: "ROLE_ADMIN,ROLE_USER"
     *
     * 실제 테이블이 다르면 SQL과 컬럼명만 맞추면 됩니다.
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        String sql = "SELECT username, password_hash, roles, enabled " +
                "FROM portal_user " +
                "WHERE username = ?";

        List<UserDetails> found = jdbcTemplate.query(sql, rs -> {
            if (!rs.next()) {
                return List.of();
            }

            String u = rs.getString("username");
            String pw = rs.getString("password_hash");
            String roles = rs.getString("roles");
            boolean enabled = rs.getBoolean("enabled");

            List<SimpleGrantedAuthority> authorities = Arrays.stream(roles.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());

            UserDetails user = User.withUsername(u)
                    .password(pw)
                    .authorities(authorities)
                    .disabled(!enabled)
                    .build();

            return List.of(user);
        }, username);

        if (found.isEmpty()) {
            throw new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + username);
        }

        return found.get(0);
    }
}
