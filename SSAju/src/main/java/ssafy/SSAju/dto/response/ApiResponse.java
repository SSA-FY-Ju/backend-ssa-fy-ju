package ssafy.SSAju.dto.response;

import java.util.Objects;

public record ApiResponse<T>(
        boolean success,
        T data,
        ErrorInfo error,
        long timestamp
) {
    public ApiResponse {
        if (success && error != null) {
            throw new IllegalArgumentException("success 응답에는 error가 없어야 합니다.");
        }
        if (!success && error == null) {
            throw new IllegalArgumentException("error 응답에는 error가 필수입니다.");
        }
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, System.currentTimeMillis());
    }

    public static <T> ApiResponse<T> error(ErrorInfo error) {
        return new ApiResponse<>(false, null, Objects.requireNonNull(error, "error must not be null"), System.currentTimeMillis());
    }
}
