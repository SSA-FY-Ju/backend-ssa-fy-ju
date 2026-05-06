package ssafy.SSAju.dto.external;

import java.util.List;
import java.util.Map;

public record FastAPIResponse(
        List<String> heavenlyStems,
        List<String> earthlyBranches,
        Map<String, Integer> fiveElements,
        String yearPillar,
        String monthPillar,
        String dayPillar,
        String hourPillar,
        String birthTime,
        String birthDate,
        Map<String, Object> solarCorrection
) {
}
