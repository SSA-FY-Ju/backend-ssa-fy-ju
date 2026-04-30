package ssafy.SSAju.dto.response;

import java.util.List;
import java.util.Map;

public record ConsultationResponse(
        List<Map<String, String>> industries,
        List<String> interviewTips,
        List<String> strengths,
        String openaiModelVersion
) {
}
