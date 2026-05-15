package ssafy.SSAju.exception;

public class DailyLimitExceededException extends DailyApiUsageException {

    public DailyLimitExceededException(String message) {
        super(message);
    }
}
