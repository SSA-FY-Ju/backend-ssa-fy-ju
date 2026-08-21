package ssafy.SSAju.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import ssafy.SSAju.career.domain.CompatibilityAnalysisData;
import ssafy.SSAju.career.entity.CompanyCompatibility;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.util.JobCategoryEnum;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.entity.enums.UserRole;
import ssafy.SSAju.entity.enums.UserStatus;
import ssafy.SSAju.exception.DataAccessException;
import ssafy.SSAju.repository.CompanyCompatibilityRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * CompanyCompatibilitySaveService 단위 테스트.
 *
 * <p>{@code @DistributedLock}으로 (사용자, 프로필, 회사, 직무, 월) 단위 경합을 막은 뒤의
 * 저장/재사용/경합 복구 분기를 검증한다. 락 자체의 동시성 보장은
 * {@code concurrency/CompanyCompatibilityConcurrencyTest}(Redis Testcontainers)에서 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CompanyCompatibilitySaveService 단위 테스트")
class CompanyCompatibilitySaveServiceTest {

    @Mock
    private CompanyCompatibilityRepository companyCompatibilityRepository;

    private CompanyCompatibilitySaveService service;

    private static final User MOCK_USER = User.builder()
            .email("test@test.com")
            .passwordHash("hash")
            .name("테스트")
            .role(UserRole.USER)
            .status(UserStatus.ACTIVE)
            .termsAgreedAt(Instant.now())
            .privacyAgreedAt(Instant.now())
            .build();

    private static final UserProfile MOCK_USER_PROFILE = UserProfile.builder()
            .birthDate(LocalDate.of(1998, 5, 7))
            .birthTime(LocalTime.of(14, 30))
            .build();

    private static final Integer COMPATIBILITY_MONTH = 202605;

    @BeforeEach
    void setUp() {
        service = new CompanyCompatibilitySaveService(companyCompatibilityRepository);
    }

    @Test
    @DisplayName("완료된 기존 행이 없으면 새로 저장한다")
    void shouldSave_WhenNoCompletedRowExists() {
        // Given
        CompanyCompatibility entity = buildEntity();
        CompatibilityAnalysisData analysisData = buildAnalysisData();
        given(companyCompatibilityRepository
                .findByUser_IdAndUserProfile_IdAndCompanyNameAndTargetRoleCategoryAndCompatibilityMonthAndCompletedTrue(
                        any(), any(), anyString(), any(), anyInt()))
                .willReturn(Optional.empty());
        given(companyCompatibilityRepository.save(entity)).willReturn(entity);

        // When
        CompanyCompatibilitySaveService.SaveOutcome outcome = service.saveWithLock(entity, analysisData);

        // Then
        assertThat(outcome.entity()).isSameAs(entity);
        assertThat(outcome.newlyCreated()).isTrue();
        verify(companyCompatibilityRepository).save(entity);
    }

    @Test
    @DisplayName("락 안에서 완료된 기존 행을 발견하면 재사용하고 저장하지 않는다")
    void shouldReuseExistingCompletedRow_WithoutInserting() {
        // Given
        CompanyCompatibility entity = buildEntity();
        CompanyCompatibility existing = buildEntity();
        existing.assignResultJsonAndMarkCompleted(buildAnalysisData());
        given(companyCompatibilityRepository
                .findByUser_IdAndUserProfile_IdAndCompanyNameAndTargetRoleCategoryAndCompatibilityMonthAndCompletedTrue(
                        any(), any(), anyString(), any(), anyInt()))
                .willReturn(Optional.of(existing));

        // When
        CompanyCompatibilitySaveService.SaveOutcome outcome = service.saveWithLock(entity, buildAnalysisData());

        // Then: 동시에 완전히 동일한 요청이 먼저 완료해 둔 행을 그대로 재사용 —
        // 우리가 방금 계산한 entity/analysisData는 저장되지 않는다(중복 방지).
        assertThat(outcome.entity()).isSameAs(existing);
        assertThat(outcome.newlyCreated()).isFalse();
        verify(companyCompatibilityRepository, never()).save(any());
    }

    @Test
    @DisplayName("삽입 중 UNIQUE 위반(락 임대시간 만료 경합) → 재조회해 완료된 행을 반환한다")
    void shouldRecoverByRefetch_WhenInsertHitsUniqueViolation() {
        // Given: 더블체크에서는 미스였지만, 삽입 시점엔 다른 스레드가 먼저 커밋해 경합이 발생
        CompanyCompatibility entity = buildEntity();
        CompanyCompatibility winner = buildEntity();
        winner.assignResultJsonAndMarkCompleted(buildAnalysisData());
        given(companyCompatibilityRepository
                .findByUser_IdAndUserProfile_IdAndCompanyNameAndTargetRoleCategoryAndCompatibilityMonthAndCompletedTrue(
                        any(), any(), anyString(), any(), anyInt()))
                .willReturn(Optional.empty())   // 더블체크: 미스
                .willReturn(Optional.of(winner)); // 삽입 실패 후 재조회: 경쟁에서 이긴 스레드의 행
        given(companyCompatibilityRepository.save(any()))
                .willThrow(new DataIntegrityViolationException("UNIQUE 제약 위반"));

        // When
        CompanyCompatibilitySaveService.SaveOutcome outcome = service.saveWithLock(entity, buildAnalysisData());

        // Then
        assertThat(outcome.entity()).isSameAs(winner);
        assertThat(outcome.newlyCreated()).isFalse();
    }

    @Test
    @DisplayName("삽입 중 UNIQUE 위반인데 재조회에서도 못 찾으면 DataAccessException을 던진다")
    void shouldThrow_WhenRecoveryRefetchAlsoFails() {
        // Given
        CompanyCompatibility entity = buildEntity();
        given(companyCompatibilityRepository
                .findByUser_IdAndUserProfile_IdAndCompanyNameAndTargetRoleCategoryAndCompatibilityMonthAndCompletedTrue(
                        any(), any(), anyString(), any(), anyInt()))
                .willReturn(Optional.empty());
        given(companyCompatibilityRepository.save(any()))
                .willThrow(new DataIntegrityViolationException("UNIQUE 제약 위반"));

        // When & Then
        assertThatThrownBy(() -> service.saveWithLock(entity, buildAnalysisData()))
                .isInstanceOf(DataAccessException.class);
    }

    private CompanyCompatibility buildEntity() {
        return CompanyCompatibility.builder()
                .userProfile(MOCK_USER_PROFILE)
                .user(MOCK_USER)
                .companyName("현대오토에버")
                .targetRoleCategory(JobCategoryEnum.TECH_BACKEND)
                .targetRoleDetailName("개발자")
                .compatibilityScore(78)
                .summary("테스트 요약")
                .compatibilityMonth(COMPATIBILITY_MONTH)
                .build();
    }

    private CompatibilityAnalysisData buildAnalysisData() {
        return new CompatibilityAnalysisData(
                new CompatibilityAnalysisData.RoleAnalysis(80, "시너지", "주의"),
                new CompatibilityAnalysisData.FiveElementsInfo(Map.of(), Map.of(), "오행 시너지"),
                new CompatibilityAnalysisData.ScoreBreakdown(70, 70, 70),
                new CompatibilityAnalysisData.StrategyInfo(List.of(), "약점 보완", List.of(), "오전"),
                List.of(), List.of(), List.of(), List.of()
        );
    }
}
