package ssafy.SSAju.dto.response;

public record ApiResponse<T>(
        boolean success,
        T data,
        ErrorInfo error,
        long timestamp
) {
    public ApiResponse {
        if (success && error != null) {
            throw new IllegalArgumentException("성공 응답에는 error가 없어야 합니다.");
        }
        if (!success && error == null) {
            throw new IllegalArgumentException("실패 응답에는 error가 필요합니다.");
        }
        if (!success && data != null) {
            throw new IllegalArgumentException("실패 응답에는 data가 없어야 합니다.");
        }
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, System.currentTimeMillis());
    }

    public static <T> ApiResponse<T> failure(ErrorInfo error) {
        return new ApiResponse<>(false, null, error, System.currentTimeMillis());
    }
}
