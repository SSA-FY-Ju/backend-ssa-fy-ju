package ssafy.SSAju.exception;

public class InvalidSajuDataException extends SajuException {

    public InvalidSajuDataException(String message) {
        super(message);
    }

    public InvalidSajuDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
