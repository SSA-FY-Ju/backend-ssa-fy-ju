package ssafy.SSAju.annotation;

/**
 * 감사 로그에 사용자 식별자를 포함시킬 수 있는 요청 DTO 인터페이스.
 *
 * <p>{@code AuditLoggingAspect}는 메서드 파라미터를 순회하며 이 인터페이스의 구현체를
 * 발견하면 {@link #getAuditUserId()}를 호출하여 userId를 추출합니다.
 * 메서드 파라미터 순서나 타입에 의존하지 않으므로 리팩토링에 안전합니다.</p>
 *
 * <p>인증 전 요청(회원가입, 로그인)처럼 userId가 아직 없는 경우 {@code null}을 반환합니다.</p>
 *
 * <pre>
 * public record SignupRequest(...) implements AuditableRequest {
 *     {@literal @}Override
 *     public Long getAuditUserId() { return null; } // 회원가입 전은 userId 없음
 * }
 * </pre>
 */
public interface AuditableRequest {

    /**
     * 감사 로그에 기록할 사용자 ID를 반환합니다.
     *
     * @return 사용자 ID, 인증 전 요청이면 {@code null}
     */
    Long getAuditUserId();
}
