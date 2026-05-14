package ssafy.SSAju.career.util;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ssafy.SSAju.career.domain.CompatibilityAnalysisData;
import ssafy.SSAju.career.domain.FiveElements;
import ssafy.SSAju.career.enums.FiveElement;
import ssafy.SSAju.career.enums.ForecastStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 궁합 분석 내부 VO({@link CompatibilityAnalysisData}) 구성 전담 클래스.
 *
 * <p>CompanyMatchingService에서 분석 데이터 생성 책임을 분리합니다.
 * 비즈니스 점수 계산은 각 Calculator 클래스에, 텍스트 생성 로직은 여기서 담당합니다.
 * 생성된 VO는 Persistence Layer에 저장되거나 CompatibilityResponse로 변환됩니다.
 */
@Component
@RequiredArgsConstructor
public class AnalysisResponseBuilder {

    private final ForecastScoreCalculator forecastScoreCalculator;
    private final RoleCompatibilityCalculator roleCompatibilityCalculator;

    /**
     * 오행 분포 분석 데이터를 빌드합니다.
     */
    public CompatibilityAnalysisData.FiveElementsInfo buildFiveElementsData(FiveElements user,
                                                                              FiveElements company) {
        String synergy = buildElementSynergyText(user, company);
        return new CompatibilityAnalysisData.FiveElementsInfo(user.asMap(), company.asMap(), synergy);
    }

    /**
     * 사용자와 기업의 오행 분포를 비교하여 상생 설명 문구를 생성합니다.
     */
    private String buildElementSynergyText(FiveElements user, FiveElements company) {
        for (String symbol : FiveElement.allSymbols()) {
            if (user.getCount(symbol) == 0 && company.getCount(symbol) > 0) {
                return String.format(
                        "기업의 강한 '%s' 기운이 사용자의 부족한 오행을 보완하는 상생 구조입니다.", symbol);
            }
        }
        return "사용자와 기업의 오행 분포가 균형 잡혀 안정적인 궁합을 보입니다.";
    }

    /**
     * 총점으로부터 세부 분석 항목을 빌드합니다.
     */
    public CompatibilityAnalysisData.ScoreBreakdown buildAnalysisBreakdown(int totalScore) {
        int characterMatch = Math.min(
                totalScore + AnalysisConstants.CHARACTER_MATCH_ADJUSTMENT,
                AnalysisConstants.MAX_SCORE);
        int potentialSynergy = Math.max(
                totalScore - AnalysisConstants.POTENTIAL_SYNERGY_ADJUSTMENT,
                AnalysisConstants.MIN_SCORE);
        return new CompatibilityAnalysisData.ScoreBreakdown(characterMatch, potentialSynergy, totalScore);
    }

    /**
     * 직군 카테고리 기반으로 실행 전략을 빌드합니다.
     */
    public CompatibilityAnalysisData.StrategyInfo buildActionableStrategy(JobCategoryEnum category) {
        String weaknessDefense = String.format(
                "%s 분야 관련 약점 질문 시, 지속적인 학습과 성장 의지를 강조하세요.",
                category.getDisplayName());
        List<String> luckyDays = List.of(
                LocalDate.now().plusDays(AnalysisConstants.LUCKY_DAY_FIRST_OFFSET).toString(),
                LocalDate.now().plusDays(AnalysisConstants.LUCKY_DAY_SECOND_OFFSET).toString(),
                LocalDate.now().plusDays(AnalysisConstants.LUCKY_DAY_THIRD_OFFSET).toString()
        );
        return new CompatibilityAnalysisData.StrategyInfo(
                category.getKeywords(), weaknessDefense, luckyDays, AnalysisConstants.PREFERRED_TIME);
    }

    /**
     * 직군 카테고리 기반으로 예상 면접 질문 목록을 빌드합니다.
     */
    public List<CompatibilityAnalysisData.InterviewQuestion> buildInterviewQuestions(JobCategoryEnum category) {
        return List.of(
                new CompatibilityAnalysisData.InterviewQuestion(
                        String.format("%s 분야에서 가장 도전적인 문제를 해결한 경험을 말씀해주세요.",
                                category.getDisplayName()),
                        "문제 해결 능력과 직군 전문성 검증"
                ),
                new CompatibilityAnalysisData.InterviewQuestion(
                        "팀 내 갈등 상황에서 어떻게 대처하셨나요?",
                        "협업 능력 및 대인관계 역량 평가"
                )
        );
    }

    /**
     * 직군 카테고리와 사용자 오행을 기반으로 역할 적합도 목록을 빌드합니다.
     */
    public List<CompatibilityAnalysisData.RoleCompatibility> buildRoleCompatibilities(
            JobCategoryEnum category, FiveElements userFiveElements) {
        int primaryScore   = roleCompatibilityCalculator.calculatePrimary(userFiveElements, category);
        int secondaryScore = roleCompatibilityCalculator.calculateSecondary(primaryScore);

        String primaryTag   = primaryScore   >= AnalysisConstants.TAG_STRONG_RECOMMEND_THRESHOLD ? "강력 추천" : "보통";
        String secondaryTag = secondaryScore >= AnalysisConstants.TAG_NORMAL_THRESHOLD            ? "보통"     : "신중 검토";

        return List.of(
                new CompatibilityAnalysisData.RoleCompatibility(
                        category.getDisplayName() + " 전문가",
                        primaryScore,
                        String.format("%s 오행 기반 적성이 높습니다.", category.getPrimaryElement()),
                        primaryTag),
                new CompatibilityAnalysisData.RoleCompatibility(
                        category.getDisplayName() + " 리드",
                        secondaryScore,
                        "리더십 역량과 기술 전문성을 함께 요구합니다.",
                        secondaryTag)
        );
    }

    /**
     * 사용자 오행 분포를 기반으로 향후 5개월 운세 데이터를 빌드합니다.
     *
     * 각 월의 계절 오행(겨울=水, 봄=木, 여름=火, 가을=金)과 사용자 오행 분포의
     * 일치 정도로 점수를 산정합니다.
     */
    public List<CompatibilityAnalysisData.MonthlyForecast> buildMonthlyForecasts(FiveElements userFiveElements) {
        int currentMonth = LocalDate.now().getMonthValue();
        List<CompatibilityAnalysisData.MonthlyForecast> forecasts = new ArrayList<>();

        for (int i = 0; i < AnalysisConstants.FORECAST_MONTH_COUNT; i++) {
            int forecastMonth = ((currentMonth - 1 + i) % 12) + 1;
            String seasonElement = FiveElement.fromMonth(forecastMonth).getSymbol();
            int elementCount = userFiveElements.getCount(seasonElement);

            int score = forecastScoreCalculator.calculate(elementCount);
            ForecastStatus status = toForecastStatus(score);
            String message = buildForecastMessage(forecastMonth, seasonElement, elementCount, status);

            forecasts.add(new CompatibilityAnalysisData.MonthlyForecast(forecastMonth, score, status, message));
        }
        return forecasts;
    }

    private ForecastStatus toForecastStatus(int score) {
        if (score >= AnalysisConstants.HIGH_COMPATIBILITY_THRESHOLD) return ForecastStatus.LUCKY;
        if (score >= AnalysisConstants.MEDIUM_COMPATIBILITY_THRESHOLD) return ForecastStatus.NORMAL;
        return ForecastStatus.CAUTION;
    }

    private String buildForecastMessage(int month, String element, int elementCount, ForecastStatus status) {
        return switch (status) {
            case LUCKY   -> String.format("%d월은 '%s' 기운이 강해 사용자의 오행과 조화를 이루는 시기입니다. 적극적인 행동을 추천합니다.", month, element);
            case CAUTION -> String.format("%d월은 '%s' 기운이 부족한 시기입니다. 신중하게 결정하세요.", month, element);
            default      -> elementCount > 0
                    ? String.format("%d월은 '%s' 기운이 안정적인 시기입니다. 꾸준한 준비를 지속하세요.", month, element)
                    : String.format("%d월은 '%s' 기운을 보완할 역량 강화에 집중하세요.", month, element);
        };
    }

    /**
     * 사용자 오행과 직군 카테고리 기반으로 주의사항 목록을 빌드합니다.
     */
    public List<String> buildCautions(FiveElements userFiveElements, JobCategoryEnum category) {
        return List.of(
                String.format("%s 분야의 빠른 변화 속도에 적응하는 시간이 필요할 수 있습니다.",
                        category.getDisplayName()),
                "초기 입사 후 조직 문화 적응에 시간이 다소 걸릴 수 있습니다."
        );
    }

    /**
     * 점수와 직군 카테고리를 기반으로 한 줄 요약 문구를 생성합니다.
     */
    public String buildSummary(int score, JobCategoryEnum category) {
        if (score >= AnalysisConstants.HIGH_COMPATIBILITY_THRESHOLD) {
            return String.format(
                    "'%s' 분야에서 사용자의 사주와 기업이 높은 시너지를 보이는 상생(相生)의 궁합입니다.",
                    category.getDisplayName());
        }
        if (score >= AnalysisConstants.MEDIUM_COMPATIBILITY_THRESHOLD) {
            return String.format(
                    "'%s' 분야에서 사용자와 기업 간 균형 잡힌 궁합을 보입니다.",
                    category.getDisplayName());
        }
        return String.format(
                "'%s' 분야에서 추가적인 역량 개발이 필요한 궁합입니다.",
                category.getDisplayName());
    }
}
