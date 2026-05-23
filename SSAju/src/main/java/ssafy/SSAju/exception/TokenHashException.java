package ssafy.SSAju.exception;

public class TokenHashException extends RuntimeException {

    public enum ErrorCode {
        ALGORITHM_NOT_AVAILABLE("SHA-256 알고리즘을 사용할 수 없습니다.");

        private final String message;

        ErrorCode(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    private final ErrorCode errorCode;

    public TokenHashException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
