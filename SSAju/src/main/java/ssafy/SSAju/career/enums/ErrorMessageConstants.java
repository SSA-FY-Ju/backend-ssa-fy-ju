package ssafy.SSAju.career.enums;

/**
 * 애플리케이션 전역 에러 메시지 상수.
 *
 * 예외 발생 시 사용되는 에러 코드와 메시지를 중앙 집중식으로 관리합니다.
 * {@link ssafy.SSAju.handler.SajuGlobalExceptionHandler}에서 이 상수들을 참조하여
 * 클라이언트에 일관된 형식의 에러 응답을 반환합니다.
 *
 * <p>에러 메시지는 다음 범주로 구분됩니다:
 * <ul>
 *   <li>GlobalExceptionHandler 응답 메시지: 최상위 HTTP 상태별 기본 메시지</li>
 *   <li>입력 검증 예외 메시지: 요청 DTO 필드 유효성 검증 실패</li>
 *   <li>FastAPI 예외 메시지: 외부 FastAPI 호출 실패</li>
 *   <li>OpenAI 예외 메시지: OpenAI API 호출 및 응답 유효성 실패</li>
 *   <li>사주 데이터 검증 예외 메시지: 천간/지지/오행 데이터 유효성 검증</li>
 *   <li>Null 검증 예외 메시지: 필수 필드 null 검증</li>
 *   <li>DB 접근 예외 메시지: 데이터베이스 조회/생성 실패</li>
 * </ul>
 *
 * @author SSAju Team
 * @see ssafy.SSAju.handler.SajuGlobalExceptionHandler
 * @see ssafy.SSAju.exception.InvalidSajuDataException
 */
public enum ErrorMessageConstants {

    // === GlobalExceptionHandler 응답 메시지 ===
    /** 사주 데이터 유효성 검증 실패 시 사용되는 에러 메시지 */
    INVALID_SAJU_DATA("INVALID_SAJU_DATA", "사주 데이터가 유효하지 않습니다."),
    /** FastAPI 타임아웃 또는 재시도 실패 시 사용되는 에러 메시지 */
    FASTAPI_TIMEOUT("FASTAPI_TIMEOUT", "Failed to fetch saju data after retries. Please try again later."),
    /** OpenAI API 호출 타임아웃 또는 실패 시 사용되는 에러 메시지 */
    OPENAI_API_TIMEOUT("OPENAI_API_TIMEOUT", "OpenAI API request failed. Please try again."),
    /** 공공 데이터베이스에서 기업 정보를 찾지 못한 경우의 에러 메시지 */
    COMPANY_NOT_FOUND("COMPANY_NOT_FOUND", "Company not found in public database. Please provide founding date."),
    /** 외부 API 호출 실패 시 사용되는 일반적인 에러 메시지 */
    EXTERNAL_API_ERROR("EXTERNAL_API_ERROR", "External API call failed. Please try again later."),
    /** 데이터베이스 접근 실패 시 사용되는 에러 메시지 */
    DATABASE_ERROR("DATABASE_ERROR", "A database error occurred. Please try again."),
    /** 입력 값 검증 실패 시 사용되는 에러 메시지 */
    VALIDATION_FAILED("VALIDATION_FAILED", "Validation failed."),
    /** 예상하지 못한 서버 에러 발생 시 사용되는 에러 메시지 */
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", "An unexpected error occurred."),

    // === 입력 검증 예외 메시지 ===
    BIRTH_DATE_REQUIRED("INVALID_SAJU_DATA", "생년월일이 필수입니다"),
    BIRTH_DATE_TOO_OLD("INVALID_SAJU_DATA", "1900년 이전 생년월일은 지원하지 않습니다. (파이썬 사주 라이브러리 제약)"),
    BIRTH_TIME_REQUIRED("INVALID_SAJU_DATA", "태어난 시간이 필수입니다 (HH:mm 형식)"),

    // === FastAPI 예외 메시지 ===
    FASTAPI_THREAD_INTERRUPTED("FASTAPI_TIMEOUT", "FastAPI 호출 중 스레드 중단됨"),
    FASTAPI_CALL_FAILED("EXTERNAL_API_ERROR", "FastAPI 호출 실패"),

    // === OpenAI 예외 메시지 ===
    OPENAI_UNAUTHORIZED("OPENAI_UNAUTHORIZED", "OpenAI API 인증 실패 - API 키를 확인해주세요."),
    OPENAI_RATE_LIMIT("OPENAI_RATE_LIMIT", "OpenAI API 사용량 초과 - 잠시 후 다시 시도해주세요."),
    OPENAI_CALL_FAILED("OPENAI_API_ERROR", "OpenAI API 호출 실패"),
    OPENAI_EMPTY_RESPONSE("OPENAI_INVALID_RESPONSE", "OpenAI 응답이 비어있습니다"),
    OPENAI_MISSING_INDUSTRIES("OPENAI_INVALID_RESPONSE", "산업 추천 정보가 누락되었습니다"),
    OPENAI_INVALID_INDUSTRY_ITEM("OPENAI_INVALID_RESPONSE", "산업 추천 항목에 빈 name 또는 reason이 포함되어 있습니다"),
    OPENAI_MISSING_INTERVIEW_TIPS("OPENAI_INVALID_RESPONSE", "면접 팁 정보가 누락되었습니다"),
    OPENAI_INVALID_INTERVIEW_ITEM("OPENAI_INVALID_RESPONSE", "면접 팁 항목에 빈 값이 포함되어 있습니다"),
    OPENAI_MISSING_STRENGTHS("OPENAI_INVALID_RESPONSE", "강점 분석 정보가 누락되었습니다"),
    OPENAI_INVALID_STRENGTH_ITEM("OPENAI_INVALID_RESPONSE", "강점 분석 항목에 빈 값이 포함되어 있습니다"),

    // === 사주 데이터 검증 예외 메시지 ===
    EARTHLY_BRANCHES_COUNT_INVALID("INVALID_SAJU_DATA", "지지 목록은 정확히 4개여야 합니다."),
    EARTHLY_BRANCHES_4_INVALID("INVALID_SAJU_DATA", "지지 목록은 정확히 4개(年月日時)여야 합니다."),
    UNKNOWN_EARTHLY_BRANCH("INVALID_SAJU_DATA", "알 수 없는 지지"),
    HEAVENLY_STEMS_COUNT_INVALID("INVALID_SAJU_DATA", "천간 목록은 정확히 4개(年月日時)여야 합니다."),
    INVALID_DAY_MASTER("INVALID_SAJU_DATA", "유효하지 않은 일간"),
    INVALID_HEAVENLY_STEM("INVALID_SAJU_DATA", "유효하지 않은 천간"),
    UNKNOWN_STEM_COMBINATION("INVALID_SAJU_DATA", "알 수 없는 천간 조합"),

    // === Null 검증 예외 메시지 ===
    SAJU_DATA_NULL("INVALID_SAJU_DATA", "사주 데이터는 null이 아니어야 합니다"),
    FIVE_ELEMENTS_REQUIRED("INVALID_SAJU_DATA", "오행(목화토금수) 데이터는 필수입니다"),
    TEN_GOD_DISTRIBUTION_NULL("INVALID_SAJU_DATA", "십신 분포 데이터가 null입니다."),
    HIDDEN_STEM_DATA_NULL("INVALID_SAJU_DATA", "지장간 데이터가 null입니다."),
    DAY_MASTER_NULL("INVALID_SAJU_DATA", "일간이 null이거나 비어있습니다."),
    TEN_GOD_DATA_NULL("INVALID_SAJU_DATA", "십신 데이터는 null이 아니어야 합니다"),
    USER_HIDDEN_STEM_NULL("INVALID_SAJU_DATA", "사용자 지장간 데이터가 null입니다."),
    USER_DAY_MASTER_NULL("INVALID_SAJU_DATA", "사용자 일간이 null이거나 비어있습니다."),
    COMPANY_HIDDEN_STEM_NULL("INVALID_SAJU_DATA", "기업 지장간 데이터가 null입니다."),
    COMPANY_DAY_MASTER_NULL("INVALID_SAJU_DATA", "기업 일간이 null이거나 비어있습니다."),
    SAJU_PROFILE_NULL("INVALID_SAJU_DATA", "userProfile과 newResult는 null이 아니어야 합니다"),
    USER_PROFILE_MISMATCH("INVALID_SAJU_DATA", "newResult의 userProfile이 전달받은 userProfile과 불일치합니다"),
    SAJU_FULL_DATA_OWNER_MISMATCH("INVALID_SAJU_DATA", "SajuFullData의 sajuResult 참조가 현재 SajuResult와 일치하지 않습니다"),

    // === DB 접근 예외 메시지 ===
    SAJU_RESULT_ACCESS_FAILED("DATABASE_ERROR", "SajuResult 조회/생성 실패"),
    USER_PROFILE_ACCESS_FAILED("DATABASE_ERROR", "UserProfile 조회/생성 실패"),

    // === 피드백 예외 메시지 ===
    SAJU_RESULT_NOT_FOUND("SAJU_RESULT_NOT_FOUND", "해당 SajuResult를 찾을 수 없습니다."),
    ANALYTICS_NOT_FOUND("ANALYTICS_NOT_FOUND", "해당 분석 기록을 찾을 수 없습니다."),
    INVALID_DATE_RANGE("INVALID_DATE_RANGE", "시작일이 종료일보다 늦을 수 없습니다."),
    INVALID_FEEDBACK_TYPE("INVALID_FEEDBACK_TYPE", "feedbackType은 CAREER_TIMING, CONSULTATION, COMPATIBILITY 중 하나여야 합니다."),

    // === 인증 예외 메시지 ===
    DUPLICATE_EMAIL("DUPLICATE_EMAIL", "이미 사용 중인 이메일입니다."),
    INVALID_CREDENTIALS("INVALID_CREDENTIALS", "이메일 또는 비밀번호가 일치하지 않습니다."),
    USER_NOT_FOUND("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
    INVALID_TOKEN("INVALID_TOKEN", "유효하지 않은 토큰입니다."),
    TOKEN_EXPIRED("TOKEN_EXPIRED", "만료된 토큰입니다."),
    DAILY_LIMIT_EXCEEDED("DAILY_LIMIT_EXCEEDED", "하루 3회 분석 제한에 도달했습니다."),
    TERMS_AGREEMENT_REQUIRED("TERMS_AGREEMENT_REQUIRED", "이용약관 및 개인정보 수집에 동의해야 합니다."),
    UNAUTHORIZED("UNAUTHORIZED", "인증이 필요합니다."),
    ACCESS_DENIED("ACCESS_DENIED", "접근 권한이 없습니다.");

    private final String code;
    private final String message;

    /**
     * 에러 메시지 상수를 생성합니다.
     *
     * @param code    에러 코드 (예: INVALID_SAJU_DATA, FASTAPI_TIMEOUT)
     * @param message 사용자에게 전달할 에러 메시지
     */
    ErrorMessageConstants(String code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 에러 코드를 반환합니다.
     *
     * @return 에러 코드 문자열
     */
    public String getCode() {
        return code;
    }

    /**
     * 에러 메시지를 반환합니다.
     *
     * @return 사용자에게 전달할 에러 메시지 문자열
     */
    public String getMessage() {
        return message;
    }
}
