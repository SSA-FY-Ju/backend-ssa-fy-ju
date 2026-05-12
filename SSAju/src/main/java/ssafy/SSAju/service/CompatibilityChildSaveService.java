package ssafy.SSAju.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.career.entity.*;
import ssafy.SSAju.dto.response.CompatibilityResponse;
import ssafy.SSAju.repository.*;

import java.util.List;

/**
 * 기업 궁합 자식 엔티티 저장 및 완료 상태 관리를 담당합니다.
 *
 * <p><strong>책임 분리 (SRP)</strong>:
 * {@link CompanyMatchingService}가 오케스트레이션에만 집중할 수 있도록
 * 자식 엔티티 저장 로직을 완전히 분리했습니다.
 *
 * <p><strong>@Transactional(REQUIRES_NEW) 적용 이유</strong>:
 * <ul>
 *   <li>8개 자식 엔티티 저장을 단일 트랜잭션으로 묶어 원자성 보장</li>
 *   <li>중간 실패 시 전체 자식 저장이 롤백되어 부분 저장 방지</li>
 *   <li>CompanyMatchingService는 @Transactional 없음(외부 I/O 중 커넥션 점유 방지) →
 *       호출자 트랜잭션이 없으므로 REQUIRES_NEW와 사실상 동일하게 동작하지만,
 *       명시적으로 선언하여 향후 @Transactional 추가 시에도 독립 트랜잭션을 보장합니다.</li>
 *   <li>같은 클래스 내 self-invocation에서는 Spring AOP 프록시가 작동하지 않으므로
 *       반드시 별도 빈(Bean)으로 분리해야 @Transactional이 적용됩니다.</li>
 * </ul>
 *
 * <p><strong>completed 플래그 (Option A)</strong>:
 * 모든 자식 저장이 성공한 후 {@code markCompleted()}를 호출하여
 * {@link CompanyMatchingService#buildResponseFromExisting}의 캐시 재사용을 허용합니다.
 * 저장 중 예외 발생 시 트랜잭션 롤백으로 자식 데이터 + completed 업데이트가 모두 취소됩니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompatibilityChildSaveService {

    private final CompanyCompatibilityRepository compatibilityRepository;
    private final TargetRoleAnalysisRepository targetRoleAnalysisRepository;
    private final FiveElementsAnalysisRepository fiveElementsAnalysisRepository;
    private final AnalysisBreakdownRepository analysisBreakdownRepository;
    private final ActionableStrategyRepository actionableStrategyRepository;
    private final ExpectedInterviewQuestionRepository expectedInterviewQuestionRepository;
    private final RoleCompatibilityRepository roleCompatibilityRepository;
    private final MonthlyForecastRepository monthlyForecastRepository;
    private final CautionRepository cautionRepository;

    /**
     * 8개 자식 엔티티를 단일 트랜잭션으로 저장하고 완료 플래그를 업데이트합니다.
     *
     * <p>중간에 예외가 발생하면 전체 저장이 롤백되어 부분 저장을 방지합니다.
     * 성공 시 {@code completed = true}로 업데이트하여 캐시 재사용을 허용합니다.
     *
     * @param saved   저장된 root 엔티티 (자식들의 FK 참조 대상)
     * @param roleAnalysis     직무 분석 결과
     * @param fiveElementsData 오행 분석 결과
     * @param breakdown        분석 세부 점수
     * @param strategy         액션 전략
     * @param questions        예상 면접 질문 목록
     * @param roles            직무 궁합 목록
     * @param forecasts        월별 운세 목록
     * @param cautions         주의사항 목록
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAllAndMarkCompleted(
            CompanyCompatibility saved,
            CompatibilityResponse.TargetRoleAnalysis roleAnalysis,
            CompatibilityResponse.FiveElements fiveElementsData,
            CompatibilityResponse.AnalysisBreakdown breakdown,
            CompatibilityResponse.ActionableStrategy strategy,
            List<CompatibilityResponse.InterviewQuestion> questions,
            List<CompatibilityResponse.RoleCompatibility> roles,
            List<CompatibilityResponse.MonthlyForecast> forecasts,
            List<String> cautions) {

        targetRoleAnalysisRepository.save(TargetRoleAnalysis.builder()
                .companyCompatibility(saved)
                .matchScore(roleAnalysis.matchScore())
                .synergy(roleAnalysis.synergy())
                .warning(roleAnalysis.warning())
                .build());

        fiveElementsAnalysisRepository.save(FiveElementsAnalysis.builder()
                .companyCompatibility(saved)
                .userDistribution(fiveElementsData.userDistribution())
                .companyDistribution(fiveElementsData.companyDistribution())
                .synergyDescription(fiveElementsData.synergyDescription())
                .build());

        analysisBreakdownRepository.save(AnalysisBreakdown.builder()
                .companyCompatibility(saved)
                .characterMatch(breakdown.characterMatch())
                .potentialSynergy(breakdown.potentialSynergy())
                .longTermStability(breakdown.longTermStability())
                .build());

        actionableStrategyRepository.save(ActionableStrategy.builder()
                .companyCompatibility(saved)
                .interviewKeywords(strategy.interviewKeywords())
                .weaknessDefense(strategy.weaknessDefense())
                .luckyDays(strategy.bestTiming().luckyDays())
                .preferredTime(strategy.bestTiming().preferredTime())
                .build());

        questions.forEach(q -> expectedInterviewQuestionRepository.save(
                ExpectedInterviewQuestion.builder()
                        .companyCompatibility(saved)
                        .question(q.question())
                        .intent(q.intent())
                        .build()));

        roles.forEach(r -> roleCompatibilityRepository.save(
                RoleCompatibility.builder()
                        .companyCompatibility(saved)
                        .roleName(r.roleName())
                        .score(r.score())
                        .reason(r.reason())
                        .tag(r.tag())
                        .build()));

        forecasts.forEach(f -> monthlyForecastRepository.save(
                MonthlyForecast.builder()
                        .companyCompatibility(saved)
                        .month(f.month())
                        .score(f.score())
                        .status(f.status())
                        .advice(f.advice())
                        .build()));

        cautions.forEach(c -> cautionRepository.save(
                Caution.builder()
                        .companyCompatibility(saved)
                        .content(c)
                        .build()));

        // Option A: 모든 자식 저장 성공 후 completed = true
        // 이 라인 이전에 예외가 발생하면 트랜잭션 롤백으로 자식들도 함께 취소됨
        compatibilityRepository.markCompleted(saved.getId());
        log.info("자식 엔티티 저장 완료 및 completed 플래그 업데이트 (compatibilityId={})", saved.getId());
    }
}
