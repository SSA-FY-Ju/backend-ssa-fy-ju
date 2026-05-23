package ssafy.SSAju.exception;

/**
 * CareerConsultation 동시 insert 경합 복구 실패 시 발생하는 예외.
 *
 * <p>UNIQUE 제약 위반 후 재조회에서도 데이터를 찾지 못한 비정상 상황에서 발생합니다.
 * {@link DataAccessException}을 상속하여 500 Internal Server Error로 처리됩니다.
 */
public class ConsultationRecoveryFailedException extends DataAccessException {

    public ConsultationRecoveryFailedException(String message) {
        super(message);
    }
}
