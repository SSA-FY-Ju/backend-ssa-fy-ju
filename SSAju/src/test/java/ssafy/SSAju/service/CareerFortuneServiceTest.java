package ssafy.SSAju.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.mapper.SajuResultMapper;
import ssafy.SSAju.career.provider.SajuAnalysisFacade;
import ssafy.SSAju.career.provider.UserProfileProvider;
import ssafy.SSAju.career.util.CareerFortuneAnalyzer;
import ssafy.SSAju.career.util.HiddenStemCalculator;
import ssafy.SSAju.career.util.TenGodCalculator;
import ssafy.SSAju.career.validator.SajuValidator;
import ssafy.SSAju.dto.external.FastAPIResponse;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.entity.enums.UserRole;
import ssafy.SSAju.entity.enums.UserStatus;
import ssafy.SSAju.exception.ExternalApiException;
import ssafy.SSAju.exception.FastAPITimeoutException;
import ssafy.SSAju.exception.InvalidSajuDataException;
import ssafy.SSAju.repository.SajuResultRepository;
import ssafy.SSAju.repository.UserRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("CareerFortuneService 단위 테스트")
class CareerFortuneServiceTest {

    @Mock private SajuDataService sajuDataService;
    @Mock private UserProfileProvider userProfileProvider;
    @Mock private SajuResultWriteService sajuResultWriteService;
    @Mock private SajuResultRepository sajuResultRepository;
    @Mock private SajuResultMapper sajuResultMapper;
    @Mock private UserRepository userRepository;

    private CareerFortuneService service;

    private final TenGodCalculator tenGodCalculator = new TenGodCalculator();
    private final HiddenStemCalculator hiddenStemCalculator = new HiddenStemCalculator();
    private final CareerFortuneAnalyzer careerFortuneAnalyzer = new CareerFortuneAnalyzer(tenGodCalculator);
    private final SajuAnalysisFacade sajuAnalysisFacade = new SajuAnalysisFacade(tenGodCalculator, hiddenStemCalculator, careerFortuneAnalyzer);
    private final SajuValidator sajuValidator = new SajuValidator();

    private static final LocalDate BIRTH_DATE = LocalDate.of(1990, 10, 10);
    private static final LocalTime BIRTH_TIME = LocalTime.of(14, 30);
    private static final Long USER_ID = 1L;

    private static final User MOCK_USER = User.builder()
            .email("test@test.com")
            .passwordHash("hash")
            .name("테스트")
            .role(UserRole.USER)
            .status(UserStatus.ACTIVE)
            .termsAgreedAt(LocalDateTime.now())
            .privacyAgreedAt(LocalDateTime.now())
            .build();

    // 일간: 己(土, 陰) / 월지: 戌 → H2 판정 / confidenceScore: 70 (지장간 포함 Analyzer 공식)
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
                sajuDataService, userProfileProvider, sajuResultWriteService, sajuResultRepository,
                sajuAnalysisFacade, sajuResultMapper, sajuValidator, userRepository);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(MOCK_USER));
    }

    // ─────────────────────────────────────────
    // 정상 플로우
    // ─────────────────────────────────────────

    @Test
    @DisplayName("신규 사용자 → UserProfile 생성 후 SajuResult 저장, H2 반환")
    void shouldCreateProfileAndSaveResult_WhenNewUser() {
        // Given
        var savedProfile = UserProfile.builder().birthDate(BIRTH_DATE).birthTime(BIRTH_TIME).build();
        var expectedResult = SajuResult.builder().userProfile(savedProfile).user(MOCK_USER).build();

        given(userProfileProvider.findOrCreate(BIRTH_DATE, BIRTH_TIME)).willReturn(savedProfile);
        given(sajuDataService.fetchSajuFromFastAPI(BIRTH_DATE, BIRTH_TIME))
                .willReturn(VALID_FASTAPI_RESPONSE);
        given(sajuResultMapper.buildSajuResult(any(), any(), any(), any(), any(), any(), anyInt(), any()))
                .willReturn(expectedResult);
        given(sajuResultWriteService.replaceForUser(MOCK_USER, savedProfile, expectedResult))
                .willReturn(expectedResult);

        // When
        var result = service.analyzeCareerTiming(BIRTH_DATE, BIRTH_TIME, USER_ID);

        // Then — 고정 입력에 대한 구체적 기대값 (己 일간, 戌 월지 → H2)
        assertThat(result.favoredPeriod()).isEqualTo("H2");
        assertThat(result.confidenceScore()).isEqualTo(70);
        assertThat(result.reasoning()).contains("하반기");
        verify(userProfileProvider).findOrCreate(BIRTH_DATE, BIRTH_TIME);
        // mapper → service → writer 배선 검증: 실제 expectedResult가 전달됐는지 확인
        verify(sajuResultWriteService).replaceForUser(MOCK_USER, savedProfile, expectedResult);
    }

    @Test
    @DisplayName("기존 사용자 → UserProfile 재사용, 동일 결과 반환")
    void shouldReuseExistingProfile_WhenUserExists() {
        // Given
        var existingProfile = UserProfile.builder().birthDate(BIRTH_DATE).birthTime(BIRTH_TIME).build();
        var expectedResult = SajuResult.builder().userProfile(existingProfile).user(MOCK_USER).build();

        given(userProfileProvider.findOrCreate(BIRTH_DATE, BIRTH_TIME)).willReturn(existingProfile);
        given(sajuDataService.fetchSajuFromFastAPI(BIRTH_DATE, BIRTH_TIME))
                .willReturn(VALID_FASTAPI_RESPONSE);
        given(sajuResultMapper.buildSajuResult(any(), any(), any(), any(), any(), any(), anyInt(), any()))
                .willReturn(expectedResult);
        given(sajuResultWriteService.replaceForUser(MOCK_USER, existingProfile, expectedResult))
                .willReturn(expectedResult);

        // When
        service.analyzeCareerTiming(BIRTH_DATE, BIRTH_TIME, USER_ID);

        // Then
        verify(userProfileProvider).findOrCreate(BIRTH_DATE, BIRTH_TIME);
        verify(sajuResultWriteService).replaceForUser(MOCK_USER, existingProfile, expectedResult);
    }

    // ─────────────────────────────────────────
    // H1 판정 케이스 — 월지가 H1 유리 지지인 경우
    // ─────────────────────────────────────────

    @Test
    @DisplayName("월지가 子(H1 유리 지지) + 관성 양수 → H1 반환")
    void shouldReturnH1_WhenMonthBranchFavorH1() {
        // Given — 일간: 己, 월지: 子 → H1 유리 지지, officerScore 양수면 H1
        var h1Response = new FastAPIResponse(
                List.of("甲", "甲", "己", "丁"),
                List.of("午", "子", "未", "寅"),
                Map.of("木", 2, "火", 1, "土", 1, "金", 0, "水", 1),
                "甲午", "甲子", "己未", "丁寅",
                "14:30", "1990-10-10", null
        );
        var savedProfile = UserProfile.builder().birthDate(BIRTH_DATE).birthTime(BIRTH_TIME).build();
        given(userProfileProvider.findOrCreate(BIRTH_DATE, BIRTH_TIME)).willReturn(savedProfile);
        given(sajuDataService.fetchSajuFromFastAPI(BIRTH_DATE, BIRTH_TIME)).willReturn(h1Response);
        var expectedResult = SajuResult.builder().userProfile(savedProfile).user(MOCK_USER).build();
        given(sajuResultMapper.buildSajuResult(any(), any(), any(), any(), any(), any(), anyInt(), any()))
                .willReturn(expectedResult);
        given(sajuResultWriteService.replaceForUser(MOCK_USER, savedProfile, expectedResult))
                .willReturn(expectedResult);

        // When
        var result = service.analyzeCareerTiming(BIRTH_DATE, BIRTH_TIME, USER_ID);

        // Then
        assertThat(result.favoredPeriod()).isEqualTo("H1");
        assertThat(result.reasoning()).contains("상반기");
    }

    // ─────────────────────────────────────────
    // FastAPI 응답 데이터 검증
    // ─────────────────────────────────────────

    @Test
    @DisplayName("FastAPI 응답의 heavenlyStems가 4개 미만 → InvalidSajuDataException")
    void shouldThrow_WhenHeavenlyStems_LessThanFour() {
        // Given
        var badResponse = new FastAPIResponse(
                List.of("庚", "甲", "己"),
                List.of("午", "戌", "未", "寅"),
                null, null, null, null, null, null, null, null
        );
        var savedProfile = UserProfile.builder().birthDate(BIRTH_DATE).birthTime(BIRTH_TIME).build();
        given(userProfileProvider.findOrCreate(BIRTH_DATE, BIRTH_TIME)).willReturn(savedProfile);
        given(sajuDataService.fetchSajuFromFastAPI(BIRTH_DATE, BIRTH_TIME)).willReturn(badResponse);

        // When & Then
        assertThatThrownBy(() -> service.analyzeCareerTiming(BIRTH_DATE, BIRTH_TIME, USER_ID))
                .isInstanceOf(InvalidSajuDataException.class)
                .hasMessageContaining("천간");
    }

    @Test
    @DisplayName("FastAPI 응답의 earthlyBranches가 4개 미만 → InvalidSajuDataException")
    void shouldThrow_WhenEarthlyBranches_LessThanFour() {
        // Given
        var badResponse = new FastAPIResponse(
                List.of("庚", "甲", "己", "丁"),
                List.of("午", "戌", "未"),
                null, null, null, null, null, null, null, null
        );
        var savedProfile = UserProfile.builder().birthDate(BIRTH_DATE).birthTime(BIRTH_TIME).build();
        given(userProfileProvider.findOrCreate(BIRTH_DATE, BIRTH_TIME)).willReturn(savedProfile);
        given(sajuDataService.fetchSajuFromFastAPI(BIRTH_DATE, BIRTH_TIME)).willReturn(badResponse);

        // When & Then
        assertThatThrownBy(() -> service.analyzeCareerTiming(BIRTH_DATE, BIRTH_TIME, USER_ID))
                .isInstanceOf(InvalidSajuDataException.class)
                .hasMessageContaining("지지");
    }

    // ─────────────────────────────────────────
    // 외부 API 예외 전파
    // ─────────────────────────────────────────

    @Test
    @DisplayName("FastAPI 타임아웃 → FastAPITimeoutException 전파")
    void shouldPropagate_FastAPITimeoutException() {
        // Given
        var savedProfile = UserProfile.builder().birthDate(BIRTH_DATE).birthTime(BIRTH_TIME).build();
        given(userProfileProvider.findOrCreate(BIRTH_DATE, BIRTH_TIME)).willReturn(savedProfile);
        given(sajuDataService.fetchSajuFromFastAPI(BIRTH_DATE, BIRTH_TIME))
                .willThrow(new FastAPITimeoutException("FastAPI 요청 시간 초과"));

        // When & Then
        assertThatThrownBy(() -> service.analyzeCareerTiming(BIRTH_DATE, BIRTH_TIME, USER_ID))
                .isInstanceOf(FastAPITimeoutException.class)
                .hasMessageContaining("시간 초과");
    }

    @Test
    @DisplayName("FastAPI 일반 오류(4xx/5xx) → ExternalApiException 전파")
    void shouldPropagate_ExternalApiException() {
        // Given
        var savedProfile = UserProfile.builder().birthDate(BIRTH_DATE).birthTime(BIRTH_TIME).build();
        given(userProfileProvider.findOrCreate(BIRTH_DATE, BIRTH_TIME)).willReturn(savedProfile);
        given(sajuDataService.fetchSajuFromFastAPI(BIRTH_DATE, BIRTH_TIME))
                .willThrow(new ExternalApiException("FastAPI 호출 실패"));

        // When & Then
        assertThatThrownBy(() -> service.analyzeCareerTiming(BIRTH_DATE, BIRTH_TIME, USER_ID))
                .isInstanceOf(ExternalApiException.class)
                .isNotInstanceOf(FastAPITimeoutException.class);
    }
}
