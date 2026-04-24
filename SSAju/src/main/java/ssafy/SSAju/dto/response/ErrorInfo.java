package ssafy.SSAju.dto.response;

public record ErrorInfo(
        String code,
        String message,
        String requestId
) {
    public static ErrorInfo of(String code, String message) {
        return new ErrorInfo(code, message, null);
    }
}
