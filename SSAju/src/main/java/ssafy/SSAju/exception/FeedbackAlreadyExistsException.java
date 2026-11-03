package ssafy.SSAju.exception;

/**
 * 같은 분석에 이미 피드백이 있는데 다시 제출한 경우.
 *
 * <p>분석당 피드백은 0개 또는 1개이며 {@code saju_id} UNIQUE 제약이 동시 중복 생성까지 막는다.
 * 재제출은 실패가 아니라 "이미 처리된 요청"이므로 409로 거부한다.
 */
public class FeedbackAlreadyExistsException extends RuntimeException {

    public FeedbackAlreadyExistsException(String message) {
        super(message);
    }

    public FeedbackAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
