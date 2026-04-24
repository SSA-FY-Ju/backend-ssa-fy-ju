package ssafy.SSAju.dto.external;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FastAPIResponse(
        @JsonProperty("year_pillar") String yearPillar,
        @JsonProperty("month_pillar") String monthPillar,
        @JsonProperty("day_pillar") String dayPillar,
        @JsonProperty("hour_pillar") String hourPillar,

        @JsonProperty("year_stem") String yearStem,
        @JsonProperty("month_stem") String monthStem,
        @JsonProperty("day_stem") String dayStem,
        @JsonProperty("hour_stem") String hourStem,

        @JsonProperty("year_branch") String yearBranch,
        @JsonProperty("month_branch") String monthBranch,
        @JsonProperty("day_branch") String dayBranch,
        @JsonProperty("hour_branch") String hourBranch,

        @JsonProperty("birth_time") String birthTime,
        @JsonProperty("birth_date") String birthDate,

        @JsonProperty("solar_correction") SolarCorrection solarCorrection
) {
    public record SolarCorrection(
            String city,
            double longitude,
            @JsonProperty("utc_offset") double utcOffset,
            @JsonProperty("correction_minutes") double correctionMinutes
    ) {}
}
