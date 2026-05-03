package ssafy.SSAju.career.enums;

public enum SuccessMessageConstants {

    CAREER_TIMING_ANALYZED("관운 분석 완료"),
    AI_CONSULTATION_COMPLETED("AI 커리어 컨설팅 완료"),
    COMPANY_COMPATIBILITY_ANALYZED("기업 궁합 분석 완료");

    private final String message;

    SuccessMessageConstants(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
