package ssafy.SSAju.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ssafy.SSAju.career.entity.*;
import ssafy.SSAju.career.util.JobCategoryEnum;
import ssafy.SSAju.dto.response.CompatibilityResponse;
import ssafy.SSAju.exception.DataAccessException;
import ssafy.SSAju.repository.*;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("CompatibilityChildReadService.buildFromExisting(saved) 단위 테스트")
class CompatibilityChildReadServiceTest {

    @Mock private TargetRoleAnalysisRepository targetRoleAnalysisRepository;
    @Mock private FiveElementsAnalysisRepository fiveElementsAnalysisRepository;
    @Mock private AnalysisBreakdownRepository analysisBreakdownRepository;
    @Mock private ActionableStrategyRepository actionableStrategyRepository;
    @Mock private ActionableKeywordRepository actionableKeywordRepository;
    @Mock private LuckyDayRepository luckyDayRepository;
    @Mock private ExpectedInterviewQuestionRepository expectedInterviewQuestionRepository;
    @Mock private RoleCompatibilityRepository roleCompatibilityRepository;
    @Mock private MonthlyForecastRepository monthlyForecastRepository;
    @Mock private CautionRepository cautionRepository;

    private CompatibilityChildReadService service;

    private static final Long COMPATIBILITY_ID = 5L;

    @BeforeEach
    void setUp() {
        service = new CompatibilityChildReadService(
                targetRoleAnalysisRepository, fiveElementsAnalysisRepository,
                analysisBreakdownRepository, actionableStrategyRepository,
                actionableKeywordRepository, luckyDayRepository,
                expectedInterviewQuestionRepository, roleCompatibilityRepository,
                monthlyForecastRepository, cautionRepository);
    }

    @Test
    @DisplayName("저장된 CompanyCompatibility → CompatibilityResponse 정상 복원")
    void shouldBuildCompatibilityResponse_FromSavedEntity() {
        // Given
        var saved = mock(CompanyCompatibility.class);
        var targetRoleAnalysis = mock(TargetRoleAnalysis.class);
        var fiveElements = mock(FiveElementsAnalysis.class);
        var analysisBreakdown = mock(AnalysisBreakdown.class);
        var actionableStrategy = mock(ActionableStrategy.class);

        given(saved.getId()).willReturn(COMPATIBILITY_ID);
        given(saved.getCompanyName()).willReturn("삼성전자");
        given(saved.getTargetRoleCategory()).willReturn(JobCategoryEnum.TECH_BACKEND);
        given(saved.getTargetRoleDetailName()).willReturn("백엔드 개발자");
        given(saved.getCompatibilityScore()).willReturn(85);
        given(saved.getSummary()).willReturn("높은 궁합");

        given(targetRoleAnalysis.getMatchScore()).willReturn(88);
        given(targetRoleAnalysis.getSynergy()).willReturn("시너지 설명");
        given(targetRoleAnalysis.getWarning()).willReturn("주의 사항");

        given(fiveElements.getUserDistribution()).willReturn(java.util.Map.of("木", 2, "火", 2, "土", 2, "金", 2, "水", 0));
        given(fiveElements.getCompanyDistribution()).willReturn(java.util.Map.of("木", 0, "火", 0, "土", 0, "金", 3, "水", 1));
        given(fiveElements.getSynergyDescription()).willReturn("오행 시너지");

        given(analysisBreakdown.getCharacterMatch()).willReturn(75);
        given(analysisBreakdown.getPotentialSynergy()).willReturn(80);
        given(analysisBreakdown.getLongTermStability()).willReturn(70);

        given(actionableStrategy.getId()).willReturn(1L);
        given(actionableStrategy.getWeaknessDefense()).willReturn("약점 보완 전략");
        given(actionableStrategy.getPreferredTime()).willReturn("오전");

        given(targetRoleAnalysisRepository.findByCompanyCompatibility_Id(COMPATIBILITY_ID))
                .willReturn(Optional.of(targetRoleAnalysis));
        given(fiveElementsAnalysisRepository.findByCompanyCompatibility_Id(COMPATIBILITY_ID))
                .willReturn(Optional.of(fiveElements));
        given(analysisBreakdownRepository.findByCompanyCompatibility_Id(COMPATIBILITY_ID))
                .willReturn(Optional.of(analysisBreakdown));
        given(actionableStrategyRepository.findByCompanyCompatibility_Id(COMPATIBILITY_ID))
                .willReturn(Optional.of(actionableStrategy));
        given(actionableKeywordRepository.findByActionableStrategy_IdOrderByDisplayOrderAsc(1L))
                .willReturn(List.of());
        given(luckyDayRepository.findByActionableStrategy_IdOrderByDisplayOrderAsc(1L))
                .willReturn(List.of());
        given(expectedInterviewQuestionRepository.findByCompanyCompatibility_Id(COMPATIBILITY_ID))
                .willReturn(List.of());
        given(roleCompatibilityRepository.findByCompanyCompatibility_Id(COMPATIBILITY_ID))
                .willReturn(List.of());
        given(monthlyForecastRepository.findByCompanyCompatibility_Id(COMPATIBILITY_ID))
                .willReturn(List.of());
        given(cautionRepository.findByCompanyCompatibility_Id(COMPATIBILITY_ID))
                .willReturn(List.of());

        // When
        CompatibilityResponse result = service.buildFromExisting(saved);

        // Then
        assertThat(result.compatibilityId()).isEqualTo(COMPATIBILITY_ID);
        assertThat(result.requestContext().companyName()).isEqualTo("삼성전자");
        assertThat(result.requestContext().targetRole().detailName()).isEqualTo("백엔드 개발자");
        assertThat(result.compatibilityScore()).isEqualTo(85);
        assertThat(result.summary()).isEqualTo("높은 궁합");
        assertThat(result.targetRoleAnalysis().matchScore()).isEqualTo(88);
        assertThat(result.fiveElements().synergyDescription()).isEqualTo("오행 시너지");
        assertThat(result.analysisBreakdown().characterMatch()).isEqualTo(75);
        assertThat(result.actionableStrategy().weaknessDefense()).isEqualTo("약점 보완 전략");
        assertThat(result.expectedInterviewQuestions()).isEmpty();
        assertThat(result.roleCompatibility()).isEmpty();
        assertThat(result.monthlyForecast()).isEmpty();
        assertThat(result.cautions()).isEmpty();
    }

    @Test
    @DisplayName("TargetRoleAnalysis 없음 → DataAccessException 발생")
    void shouldThrow_WhenTargetRoleAnalysisMissing() {
        var saved = mock(CompanyCompatibility.class);
        given(saved.getId()).willReturn(COMPATIBILITY_ID);

        given(targetRoleAnalysisRepository.findByCompanyCompatibility_Id(COMPATIBILITY_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.buildFromExisting(saved))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("TargetRoleAnalysis");
    }
}
