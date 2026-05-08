package ssafy.SSAju.career.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ssafy.SSAju.career.domain.FiveElements;
import ssafy.SSAju.dto.response.CompatibilityResponse;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JobRoleAnalyzer 단위 테스트")
class JobRoleAnalyzerTest {

    private final JobRoleAnalyzer analyzer = new JobRoleAnalyzer();

    @Test
    @DisplayName("핵심 오행 2개 이상 → matchScore 80 이상 + 강한 적성 시너지 텍스트")
    void shouldReturnHighScore_WhenPrimaryElementCountGte2() {
        // Given: 백엔드 직군의 핵심 오행(金)이 2개인 사용자
        FiveElements userFiveElements = new FiveElements(
                Map.of("木", 1, "火", 1, "土", 1, "金", 2, "水", 1));

        // When
        CompatibilityResponse.TargetRoleAnalysis result =
                analyzer.analyze(userFiveElements, JobCategoryEnum.TECH_BACKEND);

        // Then
        assertThat(result.matchScore()).isGreaterThanOrEqualTo(80);
        assertThat(result.synergy()).contains("강하게 나타나");
        assertThat(result.warning()).isNotBlank();
    }

    @Test
    @DisplayName("핵심 오행 0개 → matchScore 낮음 + 보완 노력 경고 텍스트")
    void shouldReturnLowScore_WhenNoPrimaryElement() {
        // Given: 백엔드 직군의 핵심 오행(金)이 없는 사용자
        FiveElements userFiveElements = new FiveElements(
                Map.of("木", 2, "火", 2, "土", 2, "金", 0, "水", 0));

        // When
        CompatibilityResponse.TargetRoleAnalysis result =
                analyzer.analyze(userFiveElements, JobCategoryEnum.TECH_BACKEND);

        // Then
        assertThat(result.matchScore()).isEqualTo(0);
        assertThat(result.synergy()).contains("부족하지만");
    }

    @Test
    @DisplayName("상극 오행 2개 이상 → 상극 관계 경고 텍스트 반환")
    void shouldReturnWarningText_WhenOpposingElementStrong() {
        // Given: 백엔드 직군(金)의 상극 오행(木)이 2개 이상인 사용자
        FiveElements userFiveElements = new FiveElements(
                Map.of("木", 3, "火", 0, "土", 0, "金", 0, "水", 0));

        // When
        CompatibilityResponse.TargetRoleAnalysis result =
                analyzer.analyze(userFiveElements, JobCategoryEnum.TECH_BACKEND);

        // Then
        assertThat(result.warning()).contains("상극 관계");
    }
}
