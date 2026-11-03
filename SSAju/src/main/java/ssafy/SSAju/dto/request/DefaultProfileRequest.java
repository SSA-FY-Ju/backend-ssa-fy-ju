package ssafy.SSAju.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 기본 출생정보 저장 요청.
 *
 * <p>사용자 ID나 프로필 ID를 받지 않는다 — 대상은 언제나 인증된 사용자 본인이다.
 */
public record DefaultProfileRequest(
        @NotNull(message = "생년월일은 필수입니다 (YYYY-MM-DD 형식)")
        LocalDate birthDate,

        @NotNull(message = "태어난 시간은 필수입니다 (HH:mm 형식)")
        LocalTime birthTime
) {
}
