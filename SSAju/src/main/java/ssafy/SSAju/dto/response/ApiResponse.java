package ssafy.SSAju.dto.response;

public record ApiResponse<T>(
        boolean success,
        T data,
        ErrorInfo error,
        long timestamp
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, System.currentTimeMillis());
    }

    public static <T> ApiResponse<T> failure(ErrorInfo error) {
        return new ApiResponse<>(false, null, error, System.currentTimeMillis());
    }
}
