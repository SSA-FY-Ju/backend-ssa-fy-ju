package ssafy.SSAju.career.util;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ssafy.SSAju.career.domain.CompatibilityAnalysisData;
import ssafy.SSAju.career.domain.FiveElements;
import ssafy.SSAju.career.enums.FiveElement;
import ssafy.SSAju.career.enums.ForecastStatus;
import ssafy.SSAju.career.provider.PromptProvider;
import ssafy.SSAju.dto.external.CompatibilityNarrativeResponse;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 궁합 분석 내부 VO({@link CompatibilityAnalysisData}) 구성 전담 클래스.
 *
 * <p>CompanyMatchingService에서 분석 데이터 생성 책임을 분리합니다.
 * 점수(궁합/직군매칭/역할별) 계산은 각 Calculator 클래스에서, 해설 텍스트는
 * AI({@link CompatibilityNarrativeResponse})에서 생성되며, 이 클래스는 순수 조립만 담당합니다.
 */
@Component
@RequiredArgsConstructor
public class AnalysisResponseBuilder {

    private final ForecastScoreCalculator forecastScoreCalculator;
    private final PromptProvider promptProvider;
    /** KST 기준 현재 날짜 계산용 Clock. 테스트에서 고정 시각 주입 가능. */
    private final Clock clock;

    /**
     * 오행 분포와 AI가 생성한 상생 설명 문구로 오행 분포 분석 데이터를 빌드합니다.
     */
    public CompatibilityAnalysisData.FiveElementsInfo buildFiveElementsData(FiveElements user,
                                                                              FiveElements company,
                                                                              String synergyDescription) {
        return new CompatibilityAnalysisData.FiveElementsInfo(user.asMap(), company.asMap(), synergyDescription);
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
     * 직군 카테고리와 AI가 생성한 약점 방어 전략 문구로 실행 전략을 빌드합니다.
     */
    public CompatibilityAnalysisData.StrategyInfo buildActionableStrategy(JobCategoryEnum category,
                                                                            String weaknessDefense) {
        // 자정 경계에서 서로 다른 날짜 기준으로 계산되는 것을 방지하기 위해 한 번만 호출
        LocalDate today = LocalDate.now(clock);
        List<String> luckyDays = List.of(
                today.plusDays(AnalysisConstants.LUCKY_DAY_FIRST_OFFSET).toString(),
                today.plusDays(AnalysisConstants.LUCKY_DAY_SECOND_OFFSET).toString(),
                today.plusDays(AnalysisConstants.LUCKY_DAY_THIRD_OFFSET).toString()
        );
        return new CompatibilityAnalysisData.StrategyInfo(
                category.getKeywords(), weaknessDefense, luckyDays, AnalysisConstants.PREFERRED_TIME);
    }

    /**
     * AI가 생성한 예상 면접 질문 목록을 내부 VO로 변환합니다.
     */
    public List<CompatibilityAnalysisData.InterviewQuestion> buildInterviewQuestions(
            List<CompatibilityNarrativeResponse.InterviewQuestion> aiQuestions) {
        return aiQuestions.stream()
                .map(q -> new CompatibilityAnalysisData.InterviewQuestion(q.question(), q.intent()))
                .toList();
    }

    /**
     * 이미 계산된 역할별 점수와 AI가 생성한 사유 문구로 역할 적합도 목록을 빌드합니다.
     */
    public List<CompatibilityAnalysisData.RoleCompatibility> buildRoleCompatibilities(
            JobCategoryEnum category, int primaryScore, int secondaryScore,
            String primaryReason, String secondaryReason) {
        String primaryTag   = primaryScore   >= AnalysisConstants.TAG_STRONG_RECOMMEND_THRESHOLD ? "강력 추천" : "보통";
        String secondaryTag = secondaryScore >= AnalysisConstants.TAG_NORMAL_THRESHOLD            ? "보통"     : "신중 검토";

        return List.of(
                new CompatibilityAnalysisData.RoleCompatibility(
                        category.getDisplayName() + " 전문가", primaryScore, primaryReason, primaryTag),
                new CompatibilityAnalysisData.RoleCompatibility(
                        category.getDisplayName() + " 리드", secondaryScore, secondaryReason, secondaryTag)
        );
    }

    /**
     * 사용자 오행 분포와 AI가 생성한 월별 조언으로 향후 5개월 운세 데이터를 빌드합니다.
     *
     * <p>월/점수/상태는 계절 오행과 사용자 오행 분포의 일치 정도로 규칙 기반 산정하고(변경 없음),
     * 조언 문구는 {@code monthlyAdvices}의 각 항목이 담고 있는 {@code month} 값으로 매칭한다
     * (리스트 순서에 의존하지 않음 — AI 응답 순서가 대상 월 순서와 달라도 정확히 매핑됨).
     * {@code monthlyAdvices}가 대상 월 전체를 정확히 커버함은 {@code CompanyMatchingOpenAICaller.validate}
     * 가 이미 보장하므로, 여기서는 {@code Map.get}이 항상 값을 찾는다고 가정한다.
     *
     * <p>대상 월 목록은 {@link PromptProvider#currentForecastTargetMonths()}를 그대로 재사용한다 —
     * 여기서 별도로 계산하면 AI 프롬프트/검증이 쓴 기준과 어긋날 수 있고(자정/월 경계),
     * 같은 계산 로직이 두 곳에 중복되는 것도 방지한다.
     */
    public List<CompatibilityAnalysisData.MonthlyForecast> buildMonthlyForecasts(
            FiveElements userFiveElements,
            List<CompatibilityNarrativeResponse.MonthlyAdvice> monthlyAdvices) {
        Map<Integer, String> adviceByMonth = monthlyAdvices.stream()
                .collect(Collectors.toMap(
                        CompatibilityNarrativeResponse.MonthlyAdvice::month,
                        CompatibilityNarrativeResponse.MonthlyAdvice::advice));

        List<CompatibilityAnalysisData.MonthlyForecast> forecasts = new ArrayList<>();
        for (int forecastMonth : promptProvider.currentForecastTargetMonths()) {
            String seasonElement = FiveElement.fromMonth(forecastMonth).getSymbol();
            int elementCount = userFiveElements.getCount(seasonElement);

            int score = forecastScoreCalculator.calculate(elementCount);
            ForecastStatus status = toForecastStatus(score);

            forecasts.add(new CompatibilityAnalysisData.MonthlyForecast(
                    forecastMonth, score, status, adviceByMonth.get(forecastMonth)));
        }
        return forecasts;
    }

    private ForecastStatus toForecastStatus(int score) {
        if (score >= AnalysisConstants.HIGH_COMPATIBILITY_THRESHOLD) return ForecastStatus.LUCKY;
        if (score >= AnalysisConstants.MEDIUM_COMPATIBILITY_THRESHOLD) return ForecastStatus.NORMAL;
        return ForecastStatus.CAUTION;
    }
}
