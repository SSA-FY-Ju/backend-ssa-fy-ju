package ssafy.SSAju.dto.external;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * FastAPI 사주 계산 요청 DTO.
 */
public record FastApiRequest(
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate birthDate,
        @JsonFormat(pattern = "HH:mm") LocalTime birthTime
) {}
