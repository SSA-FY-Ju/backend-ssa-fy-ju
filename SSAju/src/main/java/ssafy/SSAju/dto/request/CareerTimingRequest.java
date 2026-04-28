package ssafy.SSAju.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record CareerTimingRequest(
        @NotNull(message = "생년월일은 필수입니다 (YYYY-MM-DD 형식)")
        LocalDate birthDate,

        @NotNull(message = "태어난 시간은 필수입니다 (HH:mm 형식)")
        LocalTime birthTime
) {
}
