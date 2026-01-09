package kr.co.xai.portal.backend.controller;

import kr.co.xai.portal.backend.security.JwtTokenProvider;
import kr.co.xai.portal.backend.entity.AppUser;
import kr.co.xai.portal.backend.repository.AppUserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 회원가입 (email 기반)
     * 요청 예:
     * {
     * "email": "user@company.com",
     * "password": "pass",
     * "username": "user01", // 선택(없으면 email 앞부분 사용)
     * "fullName": "홍길동", // 선택
     * "department": "IT", // 선택
     * "position": "Manager" // 선택
     * }
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> req) {

        String email = req.get("email");
        String password = req.get("password");
        String username = req.get("username");

        if (email == null || password == null) {
            return ResponseEntity.badRequest().body("email / password 필수");
        }

        // username이 없으면 email 앞부분을 기본값으로 사용
        if (username == null || username.isBlank()) {
            username = deriveUsernameFromEmail(email);
        }

        // 중복 체크
        if (userRepository.existsByEmail(email)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("이미 존재하는 이메일");
        }
        if (userRepository.existsByUsername(username)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("이미 존재하는 username");
        }

        AppUser user = new AppUser();
        user.setEmail(email);
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRoles("ROLE_USER");

        // 선택 필드
        if (req.get("fullName") != null)
            user.setFullName(req.get("fullName"));
        if (req.get("department") != null)
            user.setDepartment(req.get("department"));
        if (req.get("position") != null)
            user.setPosition(req.get("position"));

        userRepository.save(user);

        return ResponseEntity.ok("REGISTER_OK");
    }

    /**
     * 로그인 → JWT 발급 (email 기반)
     * 요청 예:
     * {
     * "email": "user@company.com",
     * "password": "pass"
     * }
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> req) {

        String email = req.get("email");
        String password = req.get("password");

        if (email == null || password == null) {
            return ResponseEntity.badRequest().body("email / password 필수");
        }

        AppUser user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("존재하지 않는 사용자");
        }

        // ✅ Boolean 타입 체크 수정
        if (Boolean.FALSE.equals(user.getEnabled()) || Boolean.TRUE.equals(user.getAccountLocked())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("비활성 또는 잠금 계정");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            // 운영 기준: 실패 카운트 증가 + 잠금 처리(선택)
            int fail = user.getLoginFailCount() + 1;
            user.setLoginFailCount(fail);

            // 5회 실패 시 잠금(원하면 숫자 조절)
            if (fail >= 5) {
                user.setAccountLocked(true);
            }

            userRepository.save(user);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("비밀번호 불일치");
        }

        // 성공: 실패 카운트 초기화 + lastLoginAt 업데이트
        user.setLoginFailCount(0);
        user.setAccountLocked(false);
        user.setLastLoginAt(java.time.LocalDateTime.now());
        userRepository.save(user);

        // ✅ JWT subject = email
        String token = jwtTokenProvider.createAccessToken(
                user.getEmail(),
                List.of(user.getRoles()));

        return ResponseEntity.ok(
                Map.of(
                        "accessToken", token,
                        "tokenType", "Bearer"));
    }

    private String deriveUsernameFromEmail(String email) {
        int at = email.indexOf("@");
        if (at <= 0)
            return email;
        return email.substring(0, at);
    }
}