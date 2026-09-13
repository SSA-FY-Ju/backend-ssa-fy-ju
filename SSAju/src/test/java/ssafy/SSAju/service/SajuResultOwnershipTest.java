package ssafy.SSAju.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.entity.UserSajuAccess;
import ssafy.SSAju.career.provider.SajuResultProvider;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.entity.enums.UserRole;
import ssafy.SSAju.entity.enums.UserStatus;
import ssafy.SSAju.repository.SajuResultRepository;
import ssafy.SSAju.repository.UserSajuAccessRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * B1: SajuResult는 동일 생년월일시를 가진 여러 사용자가 공유하는 정본이며,
 * 어떤 사용자가 어떤 정본에 접근 가능한지는 UserSajuAccess 매핑으로 별도 관리된다(US6, T038).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SajuResultProvider 소유권 매핑(UserSajuAccess) 테스트")
class SajuResultOwnershipTest {

    @Mock private SajuResultRepository sajuResultRepository;
    @Mock private SajuResultWriteService sajuResultWriteService;
    @Mock private UserSajuAccessRepository userSajuAccessRepository;

    private SajuResultProvider provider;

    private static final UserProfile PROFILE = UserProfile.builder()
            .birthDate(LocalDate.of(1990, 10, 10))
            .birthTime(LocalTime.of(14, 30))
            .build();

    private static User userWithId(Long id) {
        User user = User.builder()
                .email("user" + id + "@test.com")
                .passwordHash("hash")
                .name("사용자" + id)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .termsAgreedAt(Instant.now())
                .privacyAgreedAt(Instant.now())
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static SajuResult sajuResultWithId(Long id) {
        SajuResult result = SajuResult.builder().userProfile(PROFILE).build();
        ReflectionTestUtils.setField(result, "id", id);
        return result;
    }

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        provider = new SajuResultProvider(sajuResultRepository, sajuResultWriteService, userSajuAccessRepository);
    }

    @Test
    @DisplayName("두 사용자가 동일 userProfile로 요청하면 같은 SajuResult.id를 공유하고 각자 별도의 UserSajuAccess를 얻는다")
    void twoUsers_shareSameSajuResult_eachGetsOwnAccessMapping() {
        // Given — 정본이 이미 존재(userA가 먼저 생성)
        SajuResult existing = sajuResultWithId(100L);
        User userA = userWithId(1L);
        User userB = userWithId(2L);
        given(sajuResultRepository.findByUserProfile(PROFILE)).willReturn(Optional.of(existing));
        given(userSajuAccessRepository.existsByUserIdAndSajuResultId(1L, 100L)).willReturn(false);
        given(userSajuAccessRepository.existsByUserIdAndSajuResultId(2L, 100L)).willReturn(false);

        // When
        SajuResult resultForA = provider.findOrCreate(userA, PROFILE, SajuResult.builder().userProfile(PROFILE).build());
        SajuResult resultForB = provider.findOrCreate(userB, PROFILE, SajuResult.builder().userProfile(PROFILE).build());

        // Then — 동일 정본을 공유
        assertThat(resultForA.getId()).isEqualTo(resultForB.getId());
        // 각 사용자마다 자신의 UserSajuAccess 매핑이 별도로 생성됨
        verify(userSajuAccessRepository).save(argMatchingAccess(1L, 100L));
        verify(userSajuAccessRepository).save(argMatchingAccess(2L, 100L));
    }

    @Test
    @DisplayName("이미 접근 매핑이 존재하는 사용자가 다시 조회하면 매핑을 중복 생성하지 않는다")
    void existingAccess_doesNotCreateDuplicateMapping() {
        // Given
        SajuResult existing = sajuResultWithId(100L);
        User user = userWithId(1L);
        given(sajuResultRepository.findByUserProfile(PROFILE)).willReturn(Optional.of(existing));
        given(userSajuAccessRepository.existsByUserIdAndSajuResultId(1L, 100L)).willReturn(true);

        // When
        provider.findOrCreate(user, PROFILE, SajuResult.builder().userProfile(PROFILE).build());

        // Then
        verify(userSajuAccessRepository, never()).save(any(UserSajuAccess.class));
    }

    private static UserSajuAccess argMatchingAccess(Long userId, Long sajuResultId) {
        return org.mockito.ArgumentMatchers.argThat(access ->
                access != null
                        && access.getUser().getId().equals(userId)
                        && access.getSajuResult().getId().equals(sajuResultId));
    }
}
