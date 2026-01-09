package kr.co.xai.portal.backend.error.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 포털 오류 / 실행 이력 공통 DTO
 * - 기존 오류 요약 + A360 Historical Activity 화면 컬럼 확장
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PortalErrorDto {

    /*
     * =========================
     * 기존 필드 (유지)
     * =========================
     */

    /** 봇 이름 */
    private String botName;

    /** 에러 코드 */
    private String errorCode;

    /** 에러 메시지 */
    private String message;

    /** 발생 시각 */
    private String occurredAt;

    /*
     * =========================
     * A360 이력 화면 확장 필드
     * =========================
     */

    /** Activity ID */
    private String activityId;

    /** 상태 (COMPLETED / FAILED / DEPLOY_FAILED 등) */
    private String status;

    /** Automation type (Task Bot, EXPORT 등) */
    private String automationType;

    /** Activity name (이력 화면 클릭 대상) */
    private String activityName;

    /** 우선순위 (PRIORITY_MEDIUM 등) */
    private String priority;

    /** 디바이스 이름 */
    private String deviceName;

    /** 실행 사용자 */
    private String userName;

    /** 시작 시각 */
    private String startedOn;

    /** 종료 시각 */
    private String endedOn;
}
