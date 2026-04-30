package ssafy.SSAju.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

public record ConsultationRequest(
        @NotNull(message = "생년월일은 필수입니다")
        LocalDate birthDate,

        @NotNull(message = "태어난 시간은 HH:mm 형식으로 필수입니다")
        LocalTime birthTime,

        @NotNull(message = "천간 데이터는 필수입니다")
        @Size(min = 4, max = 4, message = "천간은 정확히 4개여야 합니다")
        List<String> heavenlyStems,

        @NotNull(message = "지지 데이터는 필수입니다")
        @Size(min = 4, max = 4, message = "지지는 정확히 4개여야 합니다")
        List<String> earthlyBranches,

        @NotNull(message = "오행 분포는 필수입니다")
        Map<String, Integer> fiveElements,

        @NotNull(message = "지장간 데이터는 필수입니다")
        Map<String, List<String>> hiddenStems,

        @NotNull(message = "십신 분포는 필수입니다")
        Map<String, Integer> tenGodDistribution
) {
}
