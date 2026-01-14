package kr.co.xai.portal.backend.ai.service;

import kr.co.xai.portal.backend.ai.entity.AiLearningLog;
import kr.co.xai.portal.backend.ai.repository.AiLearningLogRepository;
import kr.co.xai.portal.backend.integration.a360.A360ActivityClient;
import kr.co.xai.portal.backend.integration.a360.dto.A360DeviceResponse;
import kr.co.xai.portal.backend.integration.a360.dto.A360ScheduleResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika; //  텍스트 추출 엔진 추가
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream; // [수정] InputStream 임포트 추가

@Slf4j
@Service
@RequiredArgsConstructor
public class AiLearningService {

    private final A360ActivityClient a360Client;
    private final AiLearningLogRepository learningLogRepository;
    private final Tika tika = new Tika(); // Tika 인스턴스 초기화

    /**
     * 1. A360 자산 데이터 자동 학습
     */
    @Transactional
    public String learnA360Data() {
        int learnedCount = 0;
        log.info(">> Start Learning A360 Data...");

        try {
            A360ScheduleResponse schedules = a360Client.fetchSchedules();
            if (schedules != null && schedules.getList() != null) {
                for (var item : schedules.getList()) {
                    learningLogRepository.save(AiLearningLog.builder()
                            .category("A360_SCHEDULE")
                            .targetName(item.getName())
                            .contentSummary(
                                    String.format("봇 스케줄: %s, 차기 실행: %s", item.getName(), item.getNextExecution()))
                            .status("SUCCESS")
                            .performedBy("SYSTEM")
                            .build());
                    learnedCount++;
                }
            }
        } catch (Exception e) {
            log.error("Failed to learn Schedules", e);
        }

        try {
            A360DeviceResponse devices = a360Client.fetchDevices();
            if (devices != null && devices.getList() != null) {
                for (var item : devices.getList()) {
                    learningLogRepository.save(AiLearningLog.builder()
                            .category("A360_DEVICE")
                            .targetName(item.getHostName())
                            .contentSummary(String.format("디바이스: %s, 상태: %s", item.getHostName(), item.getStatus()))
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

    /**
     * 2. 매뉴얼 문서 지식 습득 (실제 텍스트 추출 기능)
     * 문서 파일을 분석하여 본문 텍스트를 추출하고 DB에 저장합니다.
     */
    @Transactional
    public String learnManualDocument(MultipartFile file, String tag, String username) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("학습할 파일이 존재하지 않습니다.");
        }

        // [수정] try-with-resources를 사용하여 InputStream 자동 Close 처리
        try (InputStream inputStream = file.getInputStream()) {

            // 1️ [핵심] Apache Tika를 이용한 본문 텍스트 추출
            log.info(">> Extracting text from file: {}", file.getOriginalFilename());
            // 기존: String extractedText = tika.parseToString(file.getInputStream());
            // 변경: 생성한 inputStream 변수 사용
            String extractedText = tika.parseToString(inputStream);

            // 2️ 태그와 함께 추출된 텍스트를 구성
            String finalContent = String.format("[태그: %s]\n\n%s",
                    (tag == null || tag.isEmpty() ? "미분류" : tag),
                    extractedText);

            // 3️ DB에 추출된 전체 본문 저장 (AiLearningLog의 contentSummary는 TEXT 타입이므로 대용량 저장 가능)
            learningLogRepository.save(AiLearningLog.builder()
                    .category("MANUAL_DOC")
                    .targetName(file.getOriginalFilename())
                    .contentSummary(finalContent)
                    .status("SUCCESS")
                    .performedBy(username != null ? username : "admin")
                    .build());

            log.info(">> Manual Learning Success: {}", file.getOriginalFilename());
            return "문서 본문 추출 및 지식 등록이 완료되었습니다.";

        } catch (Exception e) {
            log.error("Manual Learning Failed", e);
            throw new RuntimeException("문서 분석 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    public Page<AiLearningLog> getLearningHistory(Pageable pageable) {
        return learningLogRepository.findAll(pageable);
    }
}