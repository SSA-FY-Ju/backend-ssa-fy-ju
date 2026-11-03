package ssafy.SSAju.career.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ssafy.SSAju.integration.UnifiedAnalysisTestSupport;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("엔티티 equals/hashCode JPA Proxy 안전성 테스트")
class EntityEqualsHashCodeTest {

    // ─────────────────────────────────────────
    // UserProfile
    // ─────────────────────────────────────────

    @Test
    @DisplayName("같은 id → equals true")
    void userProfile_sameId_equals() {
        var a = UserProfile.builder().birthDate(LocalDate.of(1990, 1, 1)).birthTime(LocalTime.NOON).build();
        var b = UserProfile.builder().birthDate(LocalDate.of(2000, 5, 5)).birthTime(LocalTime.MIDNIGHT).build();
        ReflectionTestUtils.setField(a, "id", 1L);
        ReflectionTestUtils.setField(b, "id", 1L);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("다른 id → equals false")
    void userProfile_differentId_notEquals() {
        var a = UserProfile.builder().birthDate(LocalDate.of(1990, 1, 1)).birthTime(LocalTime.NOON).build();
        var b = UserProfile.builder().birthDate(LocalDate.of(1990, 1, 1)).birthTime(LocalTime.NOON).build();
        ReflectionTestUtils.setField(a, "id", 1L);
        ReflectionTestUtils.setField(b, "id", 2L);

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("id null (미영속) → equals false (자기 자신 제외)")
    void userProfile_nullId_notEqualToOther() {
        var a = UserProfile.builder().birthDate(LocalDate.of(1990, 1, 1)).birthTime(LocalTime.NOON).build();
        var b = UserProfile.builder().birthDate(LocalDate.of(1990, 1, 1)).birthTime(LocalTime.NOON).build();

        assertThat(a).isNotEqualTo(b);
        assertThat(a).isEqualTo(a);
    }

    // ─────────────────────────────────────────
    // Saju (분석 결과)
    // ─────────────────────────────────────────

    @Test
    @DisplayName("Saju: 같은 id → equals true")
    void saju_sameId_equals() {
        var a = newSaju();
        var b = newSaju();
        ReflectionTestUtils.setField(a, "id", 10L);
        ReflectionTestUtils.setField(b, "id", 10L);

        assertThat(a).isEqualTo(b);
    }

    @Test
    @DisplayName("Saju: 다른 id → equals false")
    void saju_differentId_notEquals() {
        var a = newSaju();
        var b = newSaju();
        ReflectionTestUtils.setField(a, "id", 10L);
        ReflectionTestUtils.setField(b, "id", 11L);

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("Manse: 같은 id → equals true")
    void manse_sameId_equals() {
        var a = newManse();
        var b = newManse();
        ReflectionTestUtils.setField(a, "id", 20L);
        ReflectionTestUtils.setField(b, "id", 20L);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    private Manse newManse() {
        return Manse.builder()
                .userProfile(UserProfile.builder()
                        .birthDate(LocalDate.of(1990, 1, 1)).birthTime(LocalTime.NOON).build())
                .data(UnifiedAnalysisTestSupport.manseData())
                .fetchedAt(java.time.Instant.parse("2026-11-02T03:00:00Z"))
                .build();
    }

    private Saju newSaju() {
        return Saju.builder()
                .userId(1L)
                .analysisType(ssafy.SSAju.career.enums.AnalysisType.SAJU)
                .manse(newManse())
                .inputJson("{}")
                .resultJson("{}")
                .build();
    }

    // ─────────────────────────────────────────
    // hashCode는 클래스 기반 (JPA Proxy 안전)
    // ─────────────────────────────────────────

    @Test
    @DisplayName("hashCode는 id에 관계없이 같은 클래스면 동일")
    void hashCode_classBasedNotIdBased() {
        var a = UserProfile.builder().birthDate(LocalDate.of(1990, 1, 1)).birthTime(LocalTime.NOON).build();
        var b = UserProfile.builder().birthDate(LocalDate.of(2000, 2, 2)).birthTime(LocalTime.MIDNIGHT).build();
        ReflectionTestUtils.setField(a, "id", 1L);
        ReflectionTestUtils.setField(b, "id", 999L);

        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
