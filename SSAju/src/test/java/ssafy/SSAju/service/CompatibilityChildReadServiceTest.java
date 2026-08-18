package ssafy.SSAju.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ssafy.SSAju.career.domain.CompatibilityAnalysisData;
import ssafy.SSAju.career.entity.CompanyCompatibility;
import ssafy.SSAju.career.util.JobCategoryEnum;
import ssafy.SSAju.dto.response.CompatibilityResponse;
import ssafy.SSAju.exception.DataAccessException;
import ssafy.SSAju.repository.CompanyCompatibilityRepository;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("CompatibilityChildReadService.buildFromExisting(saved) 단위 테스트")
class CompatibilityChildReadServiceTest {

    @Mock private CompanyCompatibilityRepository companyCompatibilityRepository;

    private CompatibilityChildReadService service;

    private static final Long COMPATIBILITY_ID = 5L;

    @BeforeEach
    void setUp() {
        service = new CompatibilityChildReadService();
    }

    @Test
    @DisplayName("저장된 CompanyCompatibility → CompatibilityResponse 정상 복원")
    void shouldBuildCompatibilityResponse_FromSavedEntity() {
        // Given
        var saved = mock(CompanyCompatibility.class);

        CompatibilityAnalysisData data = new CompatibilityAnalysisData(
                new CompatibilityAnalysisData.RoleAnalysis(88, "시너지 설명", "주의 사항"),
                new CompatibilityAnalysisData.FiveElementsInfo(
                        Map.of("木", 2, "火", 2, "土", 2, "金", 2, "水", 0),
                        Map.of("木", 0, "火", 0, "土", 0, "金", 3, "水", 1),
                        "오행 시너지"),
                new CompatibilityAnalysisData.ScoreBreakdown(75, 80, 70),
                new CompatibilityAnalysisData.StrategyInfo(List.of(), "약점 보완 전략", List.of(), "오전"),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        given(saved.getId()).willReturn(COMPATIBILITY_ID);
        given(saved.getCompanyName()).willReturn("삼성전자");
        given(saved.getTargetRoleCategory()).willReturn(JobCategoryEnum.TECH_BACKEND);
        given(saved.getTargetRoleDetailName()).willReturn("백엔드 개발자");
        given(saved.getCompatibilityScore()).willReturn(85);
        given(saved.getSummary()).willReturn("높은 궁합");
        given(saved.getResultJson()).willReturn(data);

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
    @DisplayName("resultJson 없음 → DataAccessException 발생")
    void shouldThrow_WhenResultJsonMissing() {
        var saved = mock(CompanyCompatibility.class);

        given(saved.getId()).willReturn(COMPATIBILITY_ID);
        given(saved.getResultJson()).willReturn(null);

        assertThatThrownBy(() -> service.buildFromExisting(saved))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("resultJson");
    }
}
