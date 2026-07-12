package ssafy.SSAju.exception;

public class LockAcquisitionException extends SajuException {

    public LockAcquisitionException(String message) {
        super(message);
    }

    public LockAcquisitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
