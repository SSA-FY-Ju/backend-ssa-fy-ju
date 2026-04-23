package ssafy.SSAju.exception;

public class SajuException extends RuntimeException {

    public SajuException(String message) {
        super(message);
    }

    public SajuException(String message, Throwable cause) {
        super(message, cause);
    }
}
