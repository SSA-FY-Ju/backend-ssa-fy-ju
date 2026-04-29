package ssafy.SSAju.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.util.CareerFortuneAnalyzer;
import ssafy.SSAju.career.util.HiddenStemCalculator;
import ssafy.SSAju.career.util.TenGodCalculator;
import ssafy.SSAju.dto.external.FastAPIResponse;
import ssafy.SSAju.exception.FastAPITimeoutException;
import ssafy.SSAju.exception.InvalidSajuDataException;
import ssafy.SSAju.repository.SajuResultRepository;
import ssafy.SSAju.repository.UserProfileRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("CareerFortuneService 단위 테스트")
class CareerFortuneServiceTest {

    @Mock private SajuDataService sajuDataService;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private SajuResultRepository sajuResultRepository;

    private CareerFortuneService service;

    // 순수 계산 로직은 실제 구현체 사용
    private final TenGodCalculator tenGodCalculator = new TenGodCalculator();
    private final HiddenStemCalculator hiddenStemCalculator = new HiddenStemCalculator();
    private final CareerFortuneAnalyzer careerFortuneAnalyzer = new CareerFortuneAnalyzer(tenGodCalculator);

    private static final LocalDate BIRTH_DATE = LocalDate.of(1990, 10, 10);
    private static final LocalTime BIRTH_TIME = LocalTime.of(14, 30);

    // FastAPI가 camelCase로 응답하는 정상 케이스 (heavenlyStems, earthlyBranches 등)
    private static final FastAPIResponse VALID_FASTAPI_RESPONSE = new FastAPIResponse(
            List.of("庚", "甲", "己", "丁"),
            List.of("午", "戌", "未", "寅"),
            Map.of("木", 1, "火", 2, "土", 2, "金", 2, "水", 1),
            "庚午", "甲戌", "己未", "丁寅",
            "14:30", "1990-10-10", null
    );

    @BeforeEach
    void setUp() {
        service = new CareerFortuneService(
                sajuDataService, userProfileRepository, sajuResultRepository,
                tenGodCalculator, hiddenStemCalculator, careerFortuneAnalyzer);
    }

    // ─────────────────────────────────────────
    // 정상 플로우
    // ─────────────────────────────────────────

    @Test
    @DisplayName("신규 사용자 → UserProfile 생성 후 SajuResult 저장, H1/H2 반환")
    void shouldCreateProfileAndSaveResult_WhenNewUser() {
        // Given
        var savedProfile = UserProfile.builder().birthDate(BIRTH_DATE).birthTime(BIRTH_TIME).build();
        given(userProfileRepository.findByBirthDateAndBirthTime(BIRTH_DATE, BIRTH_TIME))
                .willReturn(Optional.empty());
        given(userProfileRepository.save(any(UserProfile.class))).willReturn(savedProfile);
        given(sajuDataService.fetchSajuFromFastAPI(BIRTH_DATE, BIRTH_TIME))
                .willReturn(VALID_FASTAPI_RESPONSE);
        given(sajuResultRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // When
        var result = service.analyzeCareerTiming(BIRTH_DATE, BIRTH_TIME);

        // Then
        assertThat(result.favoredPeriod()).isIn("H1", "H2");
        assertThat(result.confidenceScore()).isBetween(0, 100);
        assertThat(result.reasoning()).isNotBlank();
        verify(userProfileRepository).save(any(UserProfile.class));
        verify(sajuResultRepository).save(any());
    }

    @Test
    @DisplayName("기존 사용자 → UserProfile 재사용, 신규 저장 없음")
    void shouldReuseExistingProfile_WhenUserExists() {
        // Given
        var existingProfile = UserProfile.builder().birthDate(BIRTH_DATE).birthTime(BIRTH_TIME).build();
        given(userProfileRepository.findByBirthDateAndBirthTime(BIRTH_DATE, BIRTH_TIME))
                .willReturn(Optional.of(existingProfile));
        given(sajuDataService.fetchSajuFromFastAPI(BIRTH_DATE, BIRTH_TIME))
                .willReturn(VALID_FASTAPI_RESPONSE);
        given(sajuResultRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // When
        service.analyzeCareerTiming(BIRTH_DATE, BIRTH_TIME);

        // Then - UserProfile.save는 호출되지 않아야 함
        verify(userProfileRepository, never()).save(any(UserProfile.class));
        verify(sajuResultRepository).save(any());
    }

    // ─────────────────────────────────────────
    // FastAPI 응답 데이터 검증
    // ─────────────────────────────────────────

    @Test
    @DisplayName("FastAPI 응답의 heavenlyStems가 4개 미만 → InvalidSajuDataException")
    void shouldThrow_WhenHeavenlyStems_LessThanFour() {
        // Given - 3개 천간 (비정상 응답)
        var badResponse = new FastAPIResponse(
                List.of("庚", "甲", "己"),
                List.of("午", "戌", "未", "寅"),
                null, null, null, null, null, null, null, null
        );
        var savedProfile = UserProfile.builder().birthDate(BIRTH_DATE).birthTime(BIRTH_TIME).build();
        given(userProfileRepository.findByBirthDateAndBirthTime(BIRTH_DATE, BIRTH_TIME))
                .willReturn(Optional.empty());
        given(userProfileRepository.save(any())).willReturn(savedProfile);
        given(sajuDataService.fetchSajuFromFastAPI(BIRTH_DATE, BIRTH_TIME)).willReturn(badResponse);

        // When & Then
        assertThatThrownBy(() -> service.analyzeCareerTiming(BIRTH_DATE, BIRTH_TIME))
                .isInstanceOf(InvalidSajuDataException.class)
                .hasMessageContaining("천간");
    }

    @Test
    @DisplayName("FastAPI 응답의 earthlyBranches가 4개 미만 → InvalidSajuDataException")
    void shouldThrow_WhenEarthlyBranches_LessThanFour() {
        // Given - 3개 지지 (비정상 응답)
        var badResponse = new FastAPIResponse(
                List.of("庚", "甲", "己", "丁"),
                List.of("午", "戌", "未"),
                null, null, null, null, null, null, null, null
        );
        var savedProfile = UserProfile.builder().birthDate(BIRTH_DATE).birthTime(BIRTH_TIME).build();
        given(userProfileRepository.findByBirthDateAndBirthTime(BIRTH_DATE, BIRTH_TIME))
                .willReturn(Optional.empty());
        given(userProfileRepository.save(any())).willReturn(savedProfile);
        given(sajuDataService.fetchSajuFromFastAPI(BIRTH_DATE, BIRTH_TIME)).willReturn(badResponse);

        // When & Then
        assertThatThrownBy(() -> service.analyzeCareerTiming(BIRTH_DATE, BIRTH_TIME))
                .isInstanceOf(InvalidSajuDataException.class)
                .hasMessageContaining("지지");
    }

    @Test
    @DisplayName("FastAPI 타임아웃 → FastAPITimeoutException 전파")
    void shouldPropagate_FastAPITimeoutException() {
        // Given
        var savedProfile = UserProfile.builder().birthDate(BIRTH_DATE).birthTime(BIRTH_TIME).build();
        given(userProfileRepository.findByBirthDateAndBirthTime(BIRTH_DATE, BIRTH_TIME))
                .willReturn(Optional.empty());
        given(userProfileRepository.save(any())).willReturn(savedProfile);
        given(sajuDataService.fetchSajuFromFastAPI(BIRTH_DATE, BIRTH_TIME))
                .willThrow(new FastAPITimeoutException("FastAPI 요청 시간 초과"));

        // When & Then
        assertThatThrownBy(() -> service.analyzeCareerTiming(BIRTH_DATE, BIRTH_TIME))
                .isInstanceOf(FastAPITimeoutException.class)
                .hasMessageContaining("시간 초과");
    }
}
