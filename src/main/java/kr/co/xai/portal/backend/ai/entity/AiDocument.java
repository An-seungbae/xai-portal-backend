package kr.co.xai.portal.backend.ai.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_document")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 문서 유형 (RECEIPT, ID_CARD, ...) */
    @Column(name = "document_type", nullable = false, length = 30)
    private String documentType;

    /** 원본 파일명 */
    @Column(name = "source_file_name", length = 255)
    private String sourceFileName;

    /** 물리적 저장 경로 (추가된 필드) */
    @Column(name = "file_path", columnDefinition = "text")
    private String filePath;

    /**
     * AI 분석 결과 전체 JSON (jsonb)
     * Hibernate 5.x 호환 방식
     */
    @Column(name = "analysis_json", columnDefinition = "jsonb", nullable = false)
    private String analysisJson;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.documentType == null) {
            this.documentType = "UNKNOWN";
        }
    }
}