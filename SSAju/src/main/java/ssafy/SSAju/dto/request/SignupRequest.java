package ssafy.SSAju.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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
