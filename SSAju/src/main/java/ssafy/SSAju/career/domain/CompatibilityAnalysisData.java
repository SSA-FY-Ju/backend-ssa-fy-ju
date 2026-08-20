package ssafy.SSAju.career.domain;

import ssafy.SSAju.career.enums.ForecastStatus;

import java.util.List;
import java.util.Map;

/**
 * 기업 궁합 분석 결과를 담는 내부 VO (Value Object).
 *
 * <p>Persistence Layer({@link ssafy.SSAju.service.CompanyCompatibilitySaveService})가
 * Presentation Layer({@code CompatibilityResponse.*}) 타입에 의존하지 않도록 분리합니다.
 *
 * <p>사용 흐름:
 * <ol>
 *   <li>{@code CompanyMatchingService}가 {@code AnalysisResponseBuilder}로 이 VO를 생성</li>
 *   <li>{@code CompanyCompatibilitySaveService}가 이 VO를 받아 DB에 저장</li>
 *   <li>{@code CompatibilityChildReadService}가 이 VO를 {@code CompatibilityResponse}로 변환하여 반환</li>
 * </ol>
 */
public record CompatibilityAnalysisData(
        RoleAnalysis roleAnalysis,
        FiveElementsInfo fiveElements,
        ScoreBreakdown breakdown,
        StrategyInfo strategy,
        List<InterviewQuestion> questions,
        List<RoleCompatibility> roles,
        List<MonthlyForecast> forecasts,
        List<String> cautions
) {

    /** 직무 적합도 분석 결과 */
    public record RoleAnalysis(int matchScore, String synergy, String warning) {}

    /**
     * 오행 분포 분석 결과.
     *
     * <p><strong>userDistribution / companyDistribution 형식</strong>:
     * <pre>{@code
     * {
     *   "木": 2,
     *   "火": 1,
     *   "土": 0,
     *   "金": 1,
     *   "水": 0
     * }
     * }</pre>
     * 각 오행(木·火·土·金·水)의 빈도 수를 나타냅니다.
     */
    public record FiveElementsInfo(
            Map<String, Integer> userDistribution,
            Map<String, Integer> companyDistribution,
            String synergyDescription
    ) {}

    /** 분석 세부 점수 (성향 일치도, 시너지 잠재력, 장기 안정성) */
    public record ScoreBreakdown(int characterMatch, int potentialSynergy, int longTermStability) {}

    /** 지원 전략 정보 */
    public record StrategyInfo(
            List<String> keywords,
            String weaknessDefense,
            List<String> luckyDays,
            String preferredTime
    ) {}

    /** 예상 면접 질문 */
    public record InterviewQuestion(String question, String intent) {}

    /** 직무 적합도 */
    public record RoleCompatibility(String roleName, int score, String reason, String tag) {}

    /** 월별 운세 */
    public record MonthlyForecast(int month, int score, ForecastStatus status, String advice) {}
}
