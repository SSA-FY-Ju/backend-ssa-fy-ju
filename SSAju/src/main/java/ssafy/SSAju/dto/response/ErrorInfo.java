package ssafy.SSAju.dto.response;

public record ErrorInfo(
        String code,
        String message,
        String requestId
) {
}
