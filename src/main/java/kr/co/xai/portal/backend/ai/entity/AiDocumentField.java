package kr.co.xai.portal.backend.ai.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_document_field", indexes = {
        @Index(name = "idx_ai_doc_field_doc_id", columnList = "document_id"),
        @Index(name = "idx_ai_doc_field_key", columnList = "field_key")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiDocumentField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 문서 ID (FK 개념, 연관관계는 단순화) */
    @Column(name = "document_id", nullable = false)
    private Long documentId;

    /** 필드 키 */
    @Column(name = "field_key", nullable = false, length = 80)
    private String fieldKey;

    /** 필드 값 */
    @Column(name = "field_value", columnDefinition = "text")
    private String fieldValue;

    /** AI 신뢰도 */
    @Column(name = "confidence")
    private Double confidence;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
