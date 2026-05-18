package ssafy.SSAju.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 요청 DTO.
 *
 * 클라이언트로부터 전달받는 회원가입 정보를 담으며, Jakarta Validation 제약 조건을 통해
 * 입력 데이터의 유효성을 검증합니다.
 *
 * @param email 사용자 이메일 (필수, 유효한 이메일 형식)
 * @param password 비밀번호 (필수, 8자 이상)
 * @param name 사용자 이름 (필수)
 * @param termsAgreed 이용약관 동의 여부 (필수)
 * @param privacyAgreed 개인정보 수집 동의 여부 (필수)
 */
public record SignupRequest(
        @NotBlank(message = "이메일은 필수입니다")
        @Email(message = "올바른 이메일 형식이어야 합니다")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다")
        @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다")
        String password,

        @NotBlank(message = "이름은 필수입니다")
        String name,

        @NotNull(message = "이용약관 동의 여부는 필수입니다")
        Boolean termsAgreed,

        @NotNull(message = "개인정보 수집 동의 여부는 필수입니다")
        Boolean privacyAgreed
) {}
