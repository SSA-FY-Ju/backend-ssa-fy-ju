package ssafy.SSAju.career.util;

import org.springframework.stereotype.Component;
import ssafy.SSAju.career.domain.FiveElements;
import ssafy.SSAju.career.enums.FiveElement;
import ssafy.SSAju.career.enums.ForecastStatus;
import ssafy.SSAju.dto.response.CompatibilityResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * 궁합 분석 응답(CompatibilityResponse) 구성 전담 클래스.
 * <p>
 * CompanyMatchingService에서 DTO 포매팅 책임을 분리합니다.
 * 비즈니스 점수 계산은 각 Calculator 클래스에, 텍스트 생성 로직은 여기서 담당합니다.
 */
@Component
public class AnalysisResponseBuilder {

    /**
     * 오행 분포 분석 데이터를 빌드합니다.
     */
    public CompatibilityResponse.FiveElements buildFiveElementsData(FiveElements user,
                                                                      FiveElements company) {
        String synergy = buildElementSynergyText(user, company);
        return new CompatibilityResponse.FiveElements(user.asMap(), company.asMap(), synergy);
    }

    /**
     * 사용자와 기업의 오행 분포를 비교하여 상생 설명 문구를 생성합니다.
     * <p>
     * {@link FiveElement#allSymbols()}을 사용해 모든 오행을 순회합니다.
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
     * 총점으로부터 세부 분석 항목(성향 일치도, 시너지 잠재력, 장기 안정성)을 빌드합니다.
     */
    public CompatibilityResponse.AnalysisBreakdown buildAnalysisBreakdown(int totalScore) {
        int characterMatch = Math.min(
                totalScore + AnalysisConstants.CHARACTER_MATCH_ADJUSTMENT,
                AnalysisConstants.MAX_SCORE);
        int potentialSynergy = Math.max(
                totalScore - AnalysisConstants.POTENTIAL_SYNERGY_ADJUSTMENT,
                AnalysisConstants.MIN_SCORE);
        return new CompatibilityResponse.AnalysisBreakdown(characterMatch, potentialSynergy, totalScore);
    }

    /**
     * 직군 카테고리 기반으로 실행 전략을 빌드합니다.
     */
    public CompatibilityResponse.ActionableStrategy buildActionableStrategy(JobCategoryEnum category) {
        List<String> keywords = List.of("체계적 설계", "논리적 사고", "안정적 실행");
        String weaknessDefense = String.format(
                "%s 분야 관련 약점 질문 시, 지속적인 학습과 성장 의지를 강조하세요.",
                category.getDisplayName());
        List<String> luckyDays = List.of(
                LocalDate.now().plusDays(7).toString(),
                LocalDate.now().plusDays(14).toString(),
                LocalDate.now().plusDays(21).toString()
        );
        String preferredTime = "오전 09:00 ~ 11:00";
        return new CompatibilityResponse.ActionableStrategy(
                keywords, weaknessDefense,
                new CompatibilityResponse.ActionableStrategy.BestTiming(luckyDays, preferredTime)
        );
    }

    /**
     * 직군 카테고리 기반으로 예상 면접 질문 목록을 빌드합니다.
     */
    public List<CompatibilityResponse.InterviewQuestion> buildInterviewQuestions(JobCategoryEnum category) {
        return List.of(
                new CompatibilityResponse.InterviewQuestion(
                        String.format("%s 분야에서 가장 도전적인 문제를 해결한 경험을 말씀해주세요.",
                                category.getDisplayName()),
                        "문제 해결 능력과 직군 전문성 검증"
                ),
                new CompatibilityResponse.InterviewQuestion(
                        "팀 내 갈등 상황에서 어떻게 대처하셨나요?",
                        "협업 능력 및 대인관계 역량 평가"
                )
        );
    }

    /**
     * 직군 카테고리와 사용자 오행을 기반으로 역할 적합도 목록을 빌드합니다.
     */
    public List<CompatibilityResponse.RoleCompatibility> buildRoleCompatibilities(
            JobCategoryEnum category, FiveElements userFiveElements) {
        int primaryScore = Math.min(
                userFiveElements.getCount(category.getPrimaryElement())
                        * AnalysisConstants.PRIMARY_ROLE_SCORE_MULTIPLIER
                        + AnalysisConstants.PRIMARY_ROLE_SCORE_BASE,
                AnalysisConstants.MAX_SCORE);
        int secondaryScore = Math.max(
                primaryScore - AnalysisConstants.SECONDARY_ROLE_SCORE_PENALTY,
                AnalysisConstants.MIN_SCORE);

        String primaryTag = primaryScore >= AnalysisConstants.TAG_STRONG_RECOMMEND_THRESHOLD
                ? "강력 추천" : "보통";
        String secondaryTag = secondaryScore >= AnalysisConstants.TAG_NORMAL_THRESHOLD
                ? "보통" : "신중 검토";

        return List.of(
                new CompatibilityResponse.RoleCompatibility(
                        category.getDisplayName() + " 전문가",
                        primaryScore,
                        String.format("%s 오행 기반 적성이 높습니다.", category.getPrimaryElement()),
                        primaryTag
                ),
                new CompatibilityResponse.RoleCompatibility(
                        category.getDisplayName() + " 리드",
                        secondaryScore,
                        "리더십 역량과 기술 전문성을 함께 요구합니다.",
                        secondaryTag
                )
        );
    }

    /**
     * 현재 월 기준으로 향후 5개월 운세 데이터를 빌드합니다.
     */
    public List<CompatibilityResponse.MonthlyForecast> buildMonthlyForecasts() {
        int currentMonth = LocalDate.now().getMonthValue();
        return List.of(
                new CompatibilityResponse.MonthlyForecast(
                        currentMonth, 75, ForecastStatus.LUCKY, "적극적인 지원 시기입니다."),
                new CompatibilityResponse.MonthlyForecast(
                        (currentMonth % 12) + 1, 50, ForecastStatus.NORMAL, "역량 강화에 집중하세요."),
                new CompatibilityResponse.MonthlyForecast(
                        ((currentMonth + 1) % 12) + 1, 85, ForecastStatus.LUCKY, "면접 성과가 기대되는 시기입니다."),
                new CompatibilityResponse.MonthlyForecast(
                        ((currentMonth + 2) % 12) + 1, 40, ForecastStatus.CAUTION, "신중한 결정이 필요한 시기입니다."),
                new CompatibilityResponse.MonthlyForecast(
                        ((currentMonth + 3) % 12) + 1, 65, ForecastStatus.NORMAL, "꾸준한 준비가 결실을 맺는 시기입니다.")
        );
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
