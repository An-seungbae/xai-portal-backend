package kr.co.xai.portal.backend.ai.service;

import kr.co.xai.portal.backend.ai.entity.AiLearningLog;
import kr.co.xai.portal.backend.ai.repository.AiLearningLogRepository;
import kr.co.xai.portal.backend.integration.a360.A360ActivityClient;
import kr.co.xai.portal.backend.integration.a360.dto.A360DeviceResponse;
import kr.co.xai.portal.backend.integration.a360.dto.A360ScheduleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiLearningService {

    private final A360ActivityClient a360Client;
    // [변경] AiDocumentStorageService 대신 전용 Repository 사용
    private final AiLearningLogRepository learningLogRepository;
    // 실제 RAG 검색을 위해선 VectorDB나 DocumentService에도 저장은 해야 하지만,
    // 이력 관리는 Log 테이블에서 담당합니다. 여기선 로그 저장 위주로 작성합니다.

    @Transactional
    public String learnA360Data() {
        int learnedCount = 0;
        log.info(">> Start Learning A360 Data...");

        // 1. 봇 스케줄 학습
        try {
            A360ScheduleResponse schedules = a360Client.fetchSchedules();
            if (schedules != null && schedules.getList() != null) {
                for (var item : schedules.getList()) {
                    String summary = String.format("Bot: %s, Next: %s", item.getName(), item.getNextExecution());

                    // 전용 로그 테이블에 저장
                    learningLogRepository.save(AiLearningLog.builder()
                            .category("BOT_SCHEDULE")
                            .targetName(item.getName())
                            .contentSummary(summary)
                            .status("SUCCESS")
                            .performedBy("SYSTEM")
                            .build());

                    learnedCount++;
                }
            }
        } catch (Exception e) {
            log.error("Failed to learn Schedules", e);
            learningLogRepository.save(AiLearningLog.builder()
                    .category("BOT_SCHEDULE")
                    .targetName("BATCH_JOB")
                    .contentSummary("Error: " + e.getMessage())
                    .status("FAIL")
                    .performedBy("SYSTEM")
                    .build());
        }

        // 2. 디바이스 정보 학습
        try {
            A360DeviceResponse devices = a360Client.fetchDevices();
            if (devices != null && devices.getList() != null) {
                for (var item : devices.getList()) {
                    String summary = String.format("Host: %s, User: %s, Status: %s",
                            item.getHostName(), item.getUserName(), item.getStatus());

                    learningLogRepository.save(AiLearningLog.builder()
                            .category("DEVICE_INFO")
                            .targetName(item.getHostName())
                            .contentSummary(summary)
                            .status("SUCCESS")
                            .performedBy("SYSTEM")
                            .build());

                    learnedCount++;
                }
            }
        } catch (Exception e) {
            log.error("Failed to learn Devices", e);
        }

        return String.format("총 %d건의 자산 정보를 학습 이력에 기록했습니다.", learnedCount);
    }

    // [추가] 이력 조회 메서드
    public Page<AiLearningLog> getLearningHistory(Pageable pageable) {
        return learningLogRepository.findAll(pageable);
    }
}