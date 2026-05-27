package ssafy.SSAju.event;

import ssafy.SSAju.entity.enums.LoginFailureReason;

import java.time.Instant;

/**
 * 로그인 시도 이벤트.
 *
 * 로그인 성공/실패 정보를 캡슐화하는 Spring 이벤트입니다.
 * {@link ssafy.SSAju.service.AuthService}에서 발행되며,
 * {@link LoginAttemptEventListener}가 비동기로 처리하여
 * 데이터베이스에 로그인 시도 기록을 저장합니다.
 *
 * @param email 로그인 시도한 이메일 주소
 * @param success 로그인 성공 여부
 * @param failureReason 로그인 실패 사유 (성공 시 SUCCESS, 실패 시 INVALID_EMAIL 또는 WRONG_PASSWORD)
 * @param ipAddress 로그인 시도한 클라이언트 IP 주소
 * @param attemptedAt 로그인 시도 시각
 *
 * @see LoginAttemptEventListener
 * @see ssafy.SSAju.service.AuthService#publishLoginEvent
 */
public record LoginAttemptEvent(
        String email,
        boolean success,
        LoginFailureReason failureReason,
        String ipAddress,
        Instant attemptedAt
) {}
