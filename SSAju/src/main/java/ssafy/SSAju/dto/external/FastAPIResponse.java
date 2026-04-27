package ssafy.SSAju.dto.external;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record FastAPIResponse(
        @JsonProperty("heavenly_stems") List<String> heavenlyStems,
        @JsonProperty("earthly_branches") List<String> earthlyBranches,
        @JsonProperty("five_elements") Map<String, Integer> fiveElements,
        @JsonProperty("year_pillar") String yearPillar,
        @JsonProperty("month_pillar") String monthPillar,
        @JsonProperty("day_pillar") String dayPillar,
        @JsonProperty("hour_pillar") String hourPillar,
        @JsonProperty("birth_time") String birthTime,
        @JsonProperty("birth_date") String birthDate,
        @JsonProperty("solar_correction") Map<String, Object> solarCorrection
) {
}
