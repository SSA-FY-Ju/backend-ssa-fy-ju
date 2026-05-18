package ssafy.SSAju.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 이메일 중복 확인 요청 DTO.
 *
 * 회원가입 전 이메일 사용 가능 여부를 사전 확인할 때 사용됩니다.
 *
 * @param email 중복 확인할 이메일 주소 (필수, 유효한 이메일 형식)
 */
public record EmailCheckRequest(
        @NotBlank(message = "이메일은 필수입니다")
        @Email(message = "올바른 이메일 형식이어야 합니다")
        String email
) {}
