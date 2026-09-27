package ssafy.SSAju.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.mapper.SajuResultMapper;
import ssafy.SSAju.career.provider.SajuAnalysisFacade;
import ssafy.SSAju.career.provider.SajuResultProvider;
import ssafy.SSAju.career.provider.UserProfileProvider;
import ssafy.SSAju.career.util.CareerFortuneAnalyzer;
import ssafy.SSAju.career.util.HiddenStemCalculator;
import ssafy.SSAju.career.util.TenGodCalculator;
import ssafy.SSAju.career.validator.SajuValidator;
import ssafy.SSAju.dto.external.FastAPIResponse;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.entity.enums.UserRole;
import ssafy.SSAju.entity.enums.UserStatus;
import ssafy.SSAju.repository.UserRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;

/**
 * B1: 사주 정본(SajuResult)은 여러 사용자가 공유하는 캐시 가능한 자원이므로, 최초 생성이든
 * 기존 정본에 대한 최초 접근이든 일일 API 사용 쿼터를 차감하지 않는다(US6, T040).
 *
 * <p>{@link CareerFortuneService}는 {@link DailyApiUsageService}에 대한 의존성 자체를 갖지 않으므로
 * (US2 시점엔 FastAPI 성공 후 차감했으나 B1로 제거됨), {@link DailyApiUsageService#DAILY_REQUEST_LIMIT}
 * 회를 초과해 반복 호출해도 쿼터 예외 없이 항상 성공하는지로 이를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CareerFortuneService — 정본 생성/접근이 일일 쿼터를 차감하지 않는지 검증")
class CareerFortuneQuotaTest {

    @Mock private SajuDataService sajuDataService;
    @Mock private UserProfileProvider userProfileProvider;
    @Mock private SajuResultProvider sajuResultProvider;
    @Mock private SajuResultMapper sajuResultMapper;
    @Mock private UserRepository userRepository;

    private final TenGodCalculator tenGodCalculator = new TenGodCalculator();
    private final HiddenStemCalculator hiddenStemCalculator = new HiddenStemCalculator();
    private final CareerFortuneAnalyzer careerFortuneAnalyzer = new CareerFortuneAnalyzer(tenGodCalculator);
    private final SajuAnalysisFacade sajuAnalysisFacade =
            new SajuAnalysisFacade(tenGodCalculator, hiddenStemCalculator, careerFortuneAnalyzer);
    private final SajuValidator sajuValidator = new SajuValidator();

    private static final Long USER_ID = 1L;
    private static final LocalDate BIRTH_DATE = LocalDate.of(1990, 10, 10);
    private static final LocalTime BIRTH_TIME = LocalTime.of(14, 30);

    private static final User MOCK_USER = User.builder()
            .email("quota-test@test.com")
            .passwordHash("hash")
            .name("테스트")
            .role(UserRole.USER)
            .status(UserStatus.ACTIVE)
            .termsAgreedAt(Instant.now())
            .privacyAgreedAt(Instant.now())
            .build();

    private static final FastAPIResponse VALID_FASTAPI_RESPONSE = new FastAPIResponse(
            List.of("庚", "甲", "己", "丁"),
            List.of("午", "戌", "未", "寅"),
            Map.of("木", 1, "火", 2, "土", 2, "金", 2, "水", 1),
            "庚午", "甲戌", "己未", "丁寅",
            "14:30", "1990-10-10", null
    );

    @Test
    @DisplayName("DailyApiUsageService의 일일 한도(3회)보다 많이 호출해도 쿼터 예외 없이 항상 성공한다")
    void repeatedAnalysis_neverHitsQuotaLimit() {
        // Given
        CareerFortuneService service = new CareerFortuneService(
                sajuDataService, userProfileProvider, sajuResultProvider,
                sajuAnalysisFacade, sajuResultMapper, sajuValidator, userRepository);

        var savedProfile = UserProfile.builder().birthDate(BIRTH_DATE).birthTime(BIRTH_TIME).build();
        var savedResult = SajuResult.builder().userProfile(savedProfile).build();

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(MOCK_USER));
        given(userProfileProvider.findOrCreate(BIRTH_DATE, BIRTH_TIME)).willReturn(savedProfile);
        given(sajuDataService.fetchSajuFromFastAPI(BIRTH_DATE, BIRTH_TIME)).willReturn(VALID_FASTAPI_RESPONSE);
        given(sajuResultMapper.buildSajuResult(any(), any(), any(), any(), any(), anyInt(), any()))
                .willReturn(savedResult);
        given(sajuResultProvider.findOrCreate(MOCK_USER, savedProfile, savedResult)).willReturn(savedResult);

        // When & Then — DailyApiUsageService.DAILY_REQUEST_LIMIT(3)회보다 많은 5회를 호출해도
        // CareerFortuneService는 쿼터 관련 의존성이 전혀 없으므로 매번 정상 성공해야 한다.
        int callCount = DailyApiUsageService.DAILY_REQUEST_LIMIT + 2;
        for (int i = 0; i < callCount; i++) {
            var result = service.analyzeCareerTiming(BIRTH_DATE, BIRTH_TIME, USER_ID);
            assertThat(result.favoredPeriod()).isNotBlank();
        }
    }
}
