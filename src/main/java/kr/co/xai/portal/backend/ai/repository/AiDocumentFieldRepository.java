package kr.co.xai.portal.backend.ai.repository;

import kr.co.xai.portal.backend.ai.entity.AiDocumentField;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiDocumentFieldRepository extends JpaRepository<AiDocumentField, Long> {
    List<AiDocumentField> findByDocumentId(Long documentId);
}
