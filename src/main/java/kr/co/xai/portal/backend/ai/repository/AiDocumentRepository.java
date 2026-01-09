package kr.co.xai.portal.backend.ai.repository;

import kr.co.xai.portal.backend.ai.entity.AiDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiDocumentRepository extends JpaRepository<AiDocument, Long> {

    /**
     * 생성일시 내림차순 조회 (최신순)
     */
    List<AiDocument> findAllByOrderByCreatedAtDesc();
}