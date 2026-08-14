package ssafy.SSAju.dto.external;

import java.util.List;

public record CompatibilityNarrativeResponse(
        String summary,
        String roleSynergy,
        String roleWarning,
        String fiveElementsSynergyDescription,
        String weaknessDefense,
        List<InterviewQuestion> interviewQuestions,
        String primaryRoleReason,
        String secondaryRoleReason,
        List<MonthlyAdvice> monthlyAdvices,
        List<String> cautions
) {
    public record InterviewQuestion(String question, String intent) {}

    /**
     * 월별 조언. 월 번호를 함께 담아, 응답 배열의 순서가 대상 월 순서와 어긋나도
     * 조언 내용이 엉뚱한 달에 매핑되지 않도록 한다(리스트 위치가 아닌 month 값으로 매칭).
     */
    public record MonthlyAdvice(int month, String advice) {}
}
