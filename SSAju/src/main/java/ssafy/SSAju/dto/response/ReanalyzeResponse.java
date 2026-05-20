package ssafy.SSAju.dto.response;

public record ReanalyzeResponse(
        String message,
        Long newAnalysisId,
        String type
) {}
