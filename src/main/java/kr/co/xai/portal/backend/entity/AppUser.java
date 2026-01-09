package kr.co.xai.portal.backend.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "portal_user")
@Getter
@Setter
@NoArgsConstructor // 👈 추가!
@AllArgsConstructor // 👈 추가!
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 로그인 이메일 */
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    /** 사용자 ID */
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /** 비밀번호 해시 */
    @Column(name = "password_hash", nullable = false)
    private String password;

    /** 권한 */
    @Column(nullable = false)
    private String roles;

    /** 계정 활성 */
    @Column(nullable = false)
    private Boolean enabled = true; // 👈 boolean → Boolean

    /** 계정 잠금 */
    @Column(nullable = false)
    private Boolean accountLocked = false; // 👈 boolean → Boolean

    /** 로그인 실패 횟수 */
    @Column(nullable = false)
    private Integer loginFailCount = 0; // 👈 int → Integer

    /** 마지막 로그인 */
    private LocalDateTime lastLoginAt;

    /** 사용자 정보 */
    private String fullName;
    private String department;
    private String position;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}