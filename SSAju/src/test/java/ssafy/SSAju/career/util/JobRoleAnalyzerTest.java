package ssafy.SSAju.career.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ssafy.SSAju.career.domain.FiveElements;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JobRoleAnalyzer 단위 테스트")
class JobRoleAnalyzerTest {

    private final JobRoleAnalyzer analyzer = new JobRoleAnalyzer();

    @Test
    @DisplayName("핵심 오행 2개 이상 → matchScore 80 이상")
    void shouldReturnHighScore_WhenPrimaryElementCountGte2() {
        // Given: 백엔드 직군의 핵심 오행(金)이 2개인 사용자
        FiveElements userFiveElements = new FiveElements(
                Map.of("木", 1, "火", 1, "土", 1, "金", 2, "水", 1));

        // When
        int matchScore = analyzer.analyze(userFiveElements, JobCategoryEnum.TECH_BACKEND);

        // Then
        assertThat(matchScore).isGreaterThanOrEqualTo(80);
    }

    @Test
    @DisplayName("핵심 오행 0개 → matchScore 0")
    void shouldReturnLowScore_WhenNoPrimaryElement() {
        // Given: 백엔드 직군의 핵심 오행(金)이 없는 사용자
        FiveElements userFiveElements = new FiveElements(
                Map.of("木", 2, "火", 2, "土", 2, "金", 0, "水", 0));

        // When
        int matchScore = analyzer.analyze(userFiveElements, JobCategoryEnum.TECH_BACKEND);

        // Then
        assertThat(matchScore).isEqualTo(0);
    }

    @Test
    @DisplayName("핵심 오행과 보조 오행 모두 있으면 두 가중치가 모두 반영된다")
    void shouldCombinePrimaryAndSecondaryWeights() {
        // Given: 백엔드 직군의 핵심 오행(金) 1개 + 보조 오행(水) 1개
        FiveElements userFiveElements = new FiveElements(
                Map.of("木", 0, "火", 0, "土", 0, "金", 1, "水", 1));

        // When
        int matchScore = analyzer.analyze(userFiveElements, JobCategoryEnum.TECH_BACKEND);

        // Then: primaryCount(1)*40 + secondaryCount(1)*20 = 60
        assertThat(matchScore).isEqualTo(60);
    }

    @Test
    @DisplayName("가중치 합산이 100을 넘으면 100으로 상한이 적용된다")
    void shouldCapAtMaxScore() {
        // Given: 핵심 오행 3개(과다) — cap 확인용
        FiveElements userFiveElements = new FiveElements(
                Map.of("木", 0, "火", 0, "土", 0, "金", 3, "水", 0));

        // When
        int matchScore = analyzer.analyze(userFiveElements, JobCategoryEnum.TECH_BACKEND);

        // Then
        assertThat(matchScore).isEqualTo(100);
    }

    @Test
    @DisplayName("비정상적으로 음수 오행 카운트가 들어와도 0 미만으로 내려가지 않는다 (하한 클램프)")
    void shouldNotGoBelowZero_WhenElementCountIsNegative() {
        // Given: 정상적으로는 발생하지 않는 음수 카운트를 직접 주입 (방어적 하한 클램프 검증용)
        FiveElements userFiveElements = new FiveElements(
                Map.of("木", 0, "火", 0, "土", 0, "金", -5, "水", -3));

        // When
        int matchScore = analyzer.analyze(userFiveElements, JobCategoryEnum.TECH_BACKEND);

        // Then
        assertThat(matchScore).isEqualTo(0);
    }
}
