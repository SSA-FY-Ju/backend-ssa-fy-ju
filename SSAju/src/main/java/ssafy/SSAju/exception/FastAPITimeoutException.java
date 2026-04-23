package ssafy.SSAju.exception;

public class FastAPITimeoutException extends ExternalApiException {

    public FastAPITimeoutException(String message) {
        super(message);
    }

    public FastAPITimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
