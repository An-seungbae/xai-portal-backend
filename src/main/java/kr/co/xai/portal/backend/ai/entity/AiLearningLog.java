package kr.co.xai.portal.backend.ai.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_learning_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiLearningLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 학습 유형 (예: A360_SCHEDULE, A360_DEVICE, MANUAL_DOC)
    @Column(nullable = false)
    private String category;

    // 학습 대상 명칭 (예: FinanceBot, Runner-01)
    private String targetName;

    // 학습된 내용 요약 (또는 전체 텍스트)
    @Column(columnDefinition = "TEXT")
    private String contentSummary;

    // 성공 여부 (SUCCESS, FAIL)
    private String status;

    // 작업자 (SYSTEM, admin)
    private String performedBy;

    @CreationTimestamp
    private LocalDateTime learnedAt;
}