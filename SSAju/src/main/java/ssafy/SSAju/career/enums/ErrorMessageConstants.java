package ssafy.SSAju.career.enums;

public enum ErrorMessageConstants {

    // === GlobalExceptionHandler 응답 메시지 ===
    INVALID_SAJU_DATA("INVALID_SAJU_DATA", "사주 데이터가 유효하지 않습니다."),
    FASTAPI_TIMEOUT("FASTAPI_TIMEOUT", "Failed to fetch saju data after retries. Please try again later."),
    OPENAI_API_TIMEOUT("OPENAI_API_TIMEOUT", "OpenAI API request failed. Please try again."),
    COMPANY_NOT_FOUND("COMPANY_NOT_FOUND", "Company not found in public database. Please provide founding date."),
    EXTERNAL_API_ERROR("EXTERNAL_API_ERROR", "External API call failed. Please try again later."),
    DATABASE_ERROR("DATABASE_ERROR", "A database error occurred. Please try again."),
    VALIDATION_FAILED("VALIDATION_FAILED", "Validation failed."),
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", "An unexpected error occurred."),

    // === 입력 검증 예외 메시지 ===
    BIRTH_DATE_REQUIRED("INVALID_SAJU_DATA", "생년월일이 필수입니다"),
    BIRTH_TIME_REQUIRED("INVALID_SAJU_DATA", "태어난 시간이 필수입니다 (HH:mm 형식)"),

    // === FastAPI 예외 메시지 ===
    FASTAPI_THREAD_INTERRUPTED("FASTAPI_TIMEOUT", "FastAPI 호출 중 스레드 중단됨"),
    FASTAPI_CALL_FAILED("EXTERNAL_API_ERROR", "FastAPI 호출 실패"),

    // === OpenAI 예외 메시지 ===
    OPENAI_CALL_FAILED("OPENAI_API_TIMEOUT", "OpenAI API 호출 실패"),
    OPENAI_EMPTY_RESPONSE("OPENAI_API_TIMEOUT", "OpenAI 응답이 비어있습니다"),
    OPENAI_MISSING_INDUSTRIES("OPENAI_API_TIMEOUT", "산업 추천 정보가 누락되었습니다"),
    OPENAI_INVALID_INDUSTRY_ITEM("OPENAI_API_TIMEOUT", "산업 추천 항목에 빈 name 또는 reason이 포함되어 있습니다"),
    OPENAI_MISSING_INTERVIEW_TIPS("OPENAI_API_TIMEOUT", "면접 팁 정보가 누락되었습니다"),
    OPENAI_INVALID_INTERVIEW_ITEM("OPENAI_API_TIMEOUT", "면접 팁 항목에 빈 값이 포함되어 있습니다"),
    OPENAI_MISSING_STRENGTHS("OPENAI_API_TIMEOUT", "강점 분석 정보가 누락되었습니다"),
    OPENAI_INVALID_STRENGTH_ITEM("OPENAI_API_TIMEOUT", "강점 분석 항목에 빈 값이 포함되어 있습니다"),

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

    // === DB 접근 예외 메시지 ===
    SAJU_RESULT_ACCESS_FAILED("DATABASE_ERROR", "SajuResult 조회/생성 실패"),
    USER_PROFILE_ACCESS_FAILED("DATABASE_ERROR", "UserProfile 조회/생성 실패");

    private final String code;
    private final String message;

    ErrorMessageConstants(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
