package ssafy.SSAju.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record ConsultationRequest(
        @NotNull(message = "생년월일은 필수입니다")
        LocalDate birthDate,

        @NotNull(message = "태어난 시간은 HH:mm 형식으로 필수입니다")
        LocalTime birthTime
) {
}
