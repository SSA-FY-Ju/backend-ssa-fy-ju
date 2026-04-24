package ssafy.SSAju.exception;

public class PublicDataApiException extends ExternalApiException {

    public PublicDataApiException(String message) {
        super(message);
    }

    public PublicDataApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
