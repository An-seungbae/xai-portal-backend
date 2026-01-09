package kr.co.xai.portal.backend.ai.repository;

import kr.co.xai.portal.backend.ai.entity.AiLearningLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AiLearningLogRepository extends JpaRepository<AiLearningLog, Long> {
    // 최신순 조회
    List<AiLearningLog> findAllByOrderByLearnedAtDesc();
}