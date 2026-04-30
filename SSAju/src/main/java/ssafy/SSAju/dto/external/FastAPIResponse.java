package ssafy.SSAju.dto.external;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record FastAPIResponse(
        @JsonProperty("heavenlyStems") List<String> heavenlyStems,
        @JsonProperty("earthlyBranches") List<String> earthlyBranches,
        @JsonProperty("fiveElements") Map<String, Integer> fiveElements,
        @JsonProperty("yearPillar") String yearPillar,
        @JsonProperty("monthPillar") String monthPillar,
        @JsonProperty("dayPillar") String dayPillar,
        @JsonProperty("hourPillar") String hourPillar,
        @JsonProperty("birthTime") String birthTime,
        @JsonProperty("birthDate") String birthDate,
        @JsonProperty("solarCorrection") Map<String, Object> solarCorrection
) {
}
