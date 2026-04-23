package ssafy.SSAju.exception;

public class OpenAIApiException extends ExternalApiException {

    public OpenAIApiException(String message) {
        super(message);
    }

    public OpenAIApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
