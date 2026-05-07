package ssafy.SSAju.career.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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
    // SajuResult
    // ─────────────────────────────────────────

    @Test
    @DisplayName("SajuResult: 같은 id → equals true")
    void sajuResult_sameId_equals() {
        var profile = UserProfile.builder().birthDate(LocalDate.of(1990, 1, 1)).birthTime(LocalTime.NOON).build();
        var a = SajuResult.builder().userProfile(profile).build();
        var b = SajuResult.builder().userProfile(profile).build();
        ReflectionTestUtils.setField(a, "id", 10L);
        ReflectionTestUtils.setField(b, "id", 10L);

        assertThat(a).isEqualTo(b);
    }

    @Test
    @DisplayName("SajuResult: 다른 id → equals false")
    void sajuResult_differentId_notEquals() {
        var profile = UserProfile.builder().birthDate(LocalDate.of(1990, 1, 1)).birthTime(LocalTime.NOON).build();
        var a = SajuResult.builder().userProfile(profile).build();
        var b = SajuResult.builder().userProfile(profile).build();
        ReflectionTestUtils.setField(a, "id", 10L);
        ReflectionTestUtils.setField(b, "id", 11L);

        assertThat(a).isNotEqualTo(b);
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
