package ssafy.SSAju.career.enums;

/**
 * 애플리케이션 전역 성공 메시지 상수.
 *
 * API 요청이 성공적으로 처리되었을 때 사용되는 메시지를 중앙 집중식으로 관리합니다.
 * {@link ssafy.SSAju.handler.SajuGlobalExceptionHandler} 또는 컨트롤러에서 이 상수들을 참조하여
 * 클라이언트에 일관된 형식의 성공 응답을 반환합니다.
 *
 * <p>성공 메시지는 다음과 같이 구분됩니다:
 * <ul>
 *   <li>{@link #CAREER_TIMING_ANALYZED}: 관운(경력 타이밍) 분석 완료</li>
 *   <li>{@link #AI_CONSULTATION_COMPLETED}: AI 커리어 컨설팅 완료</li>
 *   <li>{@link #COMPANY_COMPATIBILITY_ANALYZED}: 기업 및 직무 궁합 분석 완료</li>
 * </ul>
 *
 * @author SSAju Team
 * @see ssafy.SSAju.controller.CareerTimingController
 * @see ssafy.SSAju.controller.ConsultationController
 */
public enum SuccessMessageConstants {

    /** 관운 분석이 성공적으로 완료되었음을 나타내는 메시지 */
    CAREER_TIMING_ANALYZED("관운 분석 완료"),
    /** AI 커리어 컨설팅이 성공적으로 완료되었음을 나타내는 메시지 */
    AI_CONSULTATION_COMPLETED("AI 커리어 컨설팅 완료"),
    /** 기업 및 직무 궁합 분석이 성공적으로 완료되었음을 나타내는 메시지 */
    COMPANY_COMPATIBILITY_ANALYZED("기업 궁합 분석 완료");

    private final String message;

    /**
     * 성공 메시지 상수를 생성합니다.
     *
     * @param message 사용자에게 전달할 성공 메시지
     */
    SuccessMessageConstants(String message) {
        this.message = message;
    }

    /**
     * 성공 메시지를 반환합니다.
     *
     * @return 사용자에게 전달할 성공 메시지 문자열
     */
    public String getMessage() {
        return message;
    }
}
