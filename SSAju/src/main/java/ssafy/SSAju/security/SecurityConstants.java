package ssafy.SSAju.security;

/**
 * 세션 보안 관련 기술 상수.
 */
public final class SecurityConstants {

    private SecurityConstants() {}

    /**
     * Access Token 블랙리스트 TTL에 더하는 고정 패딩(초).
     *
     * <p>클럭 스큐(앱 서버-Redis 서버 간 시간 오차) 및 만료 시각 계산~SETEX 사이의
     * 지연을 방어하기 위해, 계산된 남은 유효시간에 이 값을 더해 저장한다.
     */
    public static final int BLACKLIST_TTL_PADDING_SECONDS = 60;
}
