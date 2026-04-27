package ssafy.SSAju.dto.external;

import java.util.List;
import java.util.Map;

public record CareerAdviceResponse(
        List<Map<String, String>> industries,
        List<String> interviewTips,
        List<String> strengths
) {
}
