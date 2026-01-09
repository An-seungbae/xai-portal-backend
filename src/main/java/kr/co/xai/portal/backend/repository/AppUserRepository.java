package kr.co.xai.portal.backend.repository;

import kr.co.xai.portal.backend.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    Optional<AppUser> findByEmail(String email);

    Optional<AppUser> findByUsername(String username);
}
