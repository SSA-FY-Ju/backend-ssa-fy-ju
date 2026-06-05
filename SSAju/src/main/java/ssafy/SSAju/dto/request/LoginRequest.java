package ssafy.SSAju.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import ssafy.SSAju.annotation.AuditableRequest;

/**
 * 로그인 요청 DTO.
 *
 * 클라이언트로부터 전달받는 로그인 정보를 담으며, Jakarta Validation 제약 조건을 통해
 * 입력 데이터의 유효성을 검증합니다.
 *
 * <p>{@link AuditableRequest}를 구현하여 감사 로그에 userId를 노출합니다.
 * 로그인 전이므로 userId는 {@code null}을 반환합니다.</p>
 *
 * @param email 사용자 이메일 (필수, 유효한 이메일 형식)
 * @param password 비밀번호 (필수)
 */
public record LoginRequest(
        @NotBlank(message = "이메일은 필수입니다")
        @Email(message = "올바른 이메일 형식이어야 합니다")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다")
        String password
) implements AuditableRequest {

    /** 로그인 전이므로 userId 없음 */
    @Override
    public Long getAuditUserId() {
        return null;
    }
}
