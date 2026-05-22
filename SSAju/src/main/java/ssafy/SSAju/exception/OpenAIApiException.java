package ssafy.SSAju.exception;

public class OpenAIApiException extends ExternalApiException {

    private final int statusCode;

    public OpenAIApiException(String message) {
        super(message);
        this.statusCode = 0;
    }

    public OpenAIApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }

    public OpenAIApiException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
