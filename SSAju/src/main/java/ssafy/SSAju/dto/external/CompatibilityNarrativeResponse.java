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
        List<String> monthlyAdvices,
        List<String> cautions
) {
    public record InterviewQuestion(String question, String intent) {}
}
