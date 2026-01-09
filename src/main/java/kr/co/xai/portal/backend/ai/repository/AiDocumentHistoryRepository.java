package kr.co.xai.portal.backend.ai.repository;

import kr.co.xai.portal.backend.ai.entity.AiDocumentHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiDocumentHistoryRepository extends JpaRepository<AiDocumentHistory, Long> {
    List<AiDocumentHistory> findByDocumentIdOrderByCreatedAtDesc(Long documentId);
}
