package kr.co.xai.portal.backend.ai.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_document_history", indexes = {
        @Index(name = "idx_ai_doc_hist_doc_id", columnList = "document_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiDocumentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 문서 ID */
    @Column(name = "document_id", nullable = false)
    private Long documentId;

    /** CREATE, UPDATE */
    @Column(nullable = false, length = 20)
    private String action;

    /**
     * 스냅샷 JSON (jsonb)
     */
    @Column(name = "snapshot_json", columnDefinition = "jsonb", nullable = false)
    private String snapshotJson;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
