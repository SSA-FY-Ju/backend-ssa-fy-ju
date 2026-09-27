package ssafy.SSAju.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import ssafy.SSAju.career.caller.ConsultationOpenAICaller;
import ssafy.SSAju.career.domain.HiddenStems;
import ssafy.SSAju.career.domain.TenGodDistribution;
import ssafy.SSAju.career.entity.CareerConsultation;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.mapper.ConsultationMapper;
import ssafy.SSAju.career.mapper.SajuResultMapper;
import ssafy.SSAju.career.provider.SajuAnalysisFacade;
import ssafy.SSAju.career.provider.SajuResultProvider;
import ssafy.SSAju.career.provider.UserProfileProvider;
import ssafy.SSAju.career.validator.SajuValidator;
import ssafy.SSAju.dto.external.CareerAdviceResponse;
import ssafy.SSAju.dto.external.FastAPIResponse;
import ssafy.SSAju.dto.request.ConsultationRequest;
import ssafy.SSAju.dto.response.ConsultationResponse;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.entity.enums.UserRole;
import ssafy.SSAju.entity.enums.UserStatus;
import ssafy.SSAju.exception.ConsultationRecoveryFailedException;
import ssafy.SSAju.exception.OpenAIApiException;
import ssafy.SSAju.repository.CareerConsultationRepository;
import ssafy.SSAju.repository.UserRepository;
import ssafy.SSAju.service.DailyApiUsageService;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ConsultationService 단위 테스트")
class ConsultationServiceTest {

    @Mock private ConsultationOpenAICaller openAICaller;
    @Mock private SajuDataService sajuDataService;
    @Mock private SajuAnalysisFacade sajuAnalysisFacade;
    @Mock private UserProfileProvider userProfileProvider;
    @Mock private SajuResultProvider sajuResultProvider;
    @Mock private SajuResultMapper sajuResultMapper;
    @Mock private CareerConsultationRepository careerConsultationRepository;
    @Mock private ConsultationSaveService consultationSaveService;
    @Mock private UserRepository userRepository;
    @Mock private DailyApiUsageService dailyApiUsageService;

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-05-27T01:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final ConsultationMapper consultationMapper = new ConsultationMapper(FIXED_CLOCK);

    private ConsultationService service;

    private static final Long USER_ID = 1L;
    private static final User MOCK_USER = User.builder()
            .email("test@test.com")
            .passwordHash("hash")
            .name("테스트")
            .role(UserRole.USER)
            .status(UserStatus.ACTIVE)
            .termsAgreedAt(Instant.now())
            .privacyAgreedAt(Instant.now())
            .build();

    private static final LocalDate TEST_USAGE_DATE = LocalDate.of(2026, 5, 27);
    private static final LocalDate BIRTH_DATE = LocalDate.of(1990, 10, 10);
    private static final LocalTime BIRTH_TIME = LocalTime.of(14, 30);
    private static final ConsultationRequest VALID_REQUEST = new ConsultationRequest(BIRTH_DATE, BIRTH_TIME);

    private static final FastAPIResponse MOCK_SAJU = new FastAPIResponse(
            List.of("庚", "甲", "己", "丁"),
            List.of("午", "戌", "未", "寅"),
            Map.of("木", 1, "火", 2, "土", 2, "金", 2, "水", 1),
            "庚午", "甲戌", "己未", "丁寅",
            "14:30", "1990-10-10", Map.of()
    );

    private static final TenGodDistribution TEN_GOD =
            new TenGodDistribution(Map.of("정관", 1, "편관", 1));
    private static final HiddenStems HIDDEN_STEMS =
            new HiddenStems(Map.of("午", List.of("丁", "己"), "戌", List.of("丁", "辛", "戊"),
                   "未", List.of("乙", "丁", "己"), "寅", List.of("甲", "丙", "戊")));

    private static final SajuAnalysisFacade.SajuAnalysisContext MOCK_CTX_H1 =
            new SajuAnalysisFacade.SajuAnalysisContext("己", TEN_GOD, HIDDEN_STEMS, "H1", 80, "상반기가 취업에 유리합니다.");
    private static final SajuAnalysisFacade.SajuAnalysisContext MOCK_CTX_H2 =
            new SajuAnalysisFacade.SajuAnalysisContext("己", TEN_GOD, HIDDEN_STEMS, "H2", 60, "하반기가 취업에 유리합니다.");

    private static final CareerAdviceResponse MOCK_ADVICE = new CareerAdviceResponse(
            List.of(new CareerAdviceResponse.IndustryRecommendation(
                    "금융/핀테크", "오행 金 강세로 재무 분야 적합", List.of("백엔드 개발자", "데이터 엔지니어"))),
            List.of("일관성 있는 자기소개 준비", "데이터 기반 성과 강조"),
            List.of("분석력과 논리성", "책임감"),
            List.of("지나친 꼼꼼함으로 인한 업무 속도 저하 주의"),
            new CareerAdviceResponse.WealthStyle(
                    "안정적인 월급 중심", "기술 전문성으로 몸값 향상", "보수적 투자 성향", "기술 블로그 추천"),
            new CareerAdviceResponse.LongTermRoadmap(
                    new CareerAdviceResponse.PhaseAdvice("기본기 다지기", "백엔드 심화", "오픈소스 기여"),
                    new CareerAdviceResponse.PhaseAdvice("시니어 전환", "팀리드 경험", "아키텍처 참여"),
                    "CTO", "정관 기운으로 기술 방향 주도"),
            new CareerAdviceResponse.PersonalBranding(
                    "네이비 수트", "신뢰감 있는 인상", "정돈된 스타일", "책임감 있는 엔지니어", "안정과 혁신의 기술 리더"),
            new CareerAdviceResponse.PowerKeywords(
                    List.of(new CareerAdviceResponse.PowerKeyword(
                            "뿌리깊은_책임감", "土", "안정적이고 책임감 있는 성향",
                            "뿌리깊은 책임감으로 팀의 신뢰를 얻는 개발자입니다.", "자소서 첫 문장")),
                    "하나를 메인으로 선택", List.of("첫 문장 활용", "일관되게 사용"), "3개 동시 사용 금지"),
            new CareerAdviceResponse.MentalCare(
                    List.of("남의 시선을 많이 신경 쓰는 편"), List.of("혼자 조용히 산책"),
                    "완벽함은 적의다", "성과 리스트 보기"),
            new CareerAdviceResponse.EnvironmentFit(
                    "규칙과 체계가 명확한 분위기", "대기업", "경험 많은 시니어 상사", "객관적 논의 선호",
                    "햇빛 드는 창가", "기술 존중 조직"),
            new CareerAdviceResponse.WorkStyle(
                    "대기업 안정성 선호", "멘토형 리더", "신중한 정보 수집 후 결정", "시간을 두고 유연하게 대처"),
            new CareerAdviceResponse.RelationshipStrategy(
                    "조력자 스타일", "깊이 있는 관계 구축", "go-to person", "데이터 기반 논의", "전문가 네트워크"),
            new CareerAdviceResponse.CareerTimeline(
                    2026,
                    Map.of(3, new CareerAdviceResponse.MonthFortune("적극기", "면접 기회 많음")),
                    List.of(new CareerAdviceResponse.PivotPoint(3, "적극기", 9, "정관 기운의 절정")),
                    List.of(5, 7),
                    "이 기간엔 급하게 결정하지 말 것"),
            List.of("정관", "편관"),
            "己土(기토) - 수용적이고 꼼꼼한 성향",
            "火와 金의 기운이 강해 전략성과 실행력이 뛰어남"
    );

    /** MOCK_ADVICE와 strengths만 다르게 한 "경쟁에서 이긴 스레드가 실제로 저장한" advice. */
    private static final CareerAdviceResponse WINNER_ADVICE = new CareerAdviceResponse(
            MOCK_ADVICE.industries(), MOCK_ADVICE.interviewTips(),
            List.of("승자의_강점_텍스트"),
            MOCK_ADVICE.cautions(), MOCK_ADVICE.wealthStyle(), MOCK_ADVICE.longTermRoadmap(),
            MOCK_ADVICE.personalBranding(), MOCK_ADVICE.powerKeywords(), MOCK_ADVICE.mentalCare(),
            MOCK_ADVICE.environmentFit(), MOCK_ADVICE.workStyle(), MOCK_ADVICE.relationshipStrategy(),
            MOCK_ADVICE.careerTimeline(), MOCK_ADVICE.keyTenGods(),
            MOCK_ADVICE.dayMasterDescription(), MOCK_ADVICE.fiveElementsAnalysis()
    );

    @BeforeEach
    void setUp() {
        service = new ConsultationService(
                openAICaller, sajuDataService, sajuAnalysisFacade,
                userProfileProvider, sajuResultProvider, sajuResultMapper, consultationMapper,
                careerConsultationRepository, consultationSaveService, new SajuValidator(), userRepository,
                dailyApiUsageService, FIXED_CLOCK);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(MOCK_USER));
        given(dailyApiUsageService.checkAndIncrementDailyUsage(USER_ID)).willReturn(TEST_USAGE_DATE);
        try {
            var field = ConsultationService.class.getDeclaredField("modelVersion");
            field.setAccessible(true);
            field.set(service, "gpt-4o-mini");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ─────────────────────────────────────────
    // 정상 플로우 — SajuResult 기존 존재
    // ─────────────────────────────────────────

    @Test
    @DisplayName("유효한 요청 + SajuResult 존재 → 확장 컨설팅 결과 반환 및 DB 저장")
    void shouldReturnConsultation_WhenSajuResultExists() {
        var userProfile = UserProfile.builder().birthDate(BIRTH_DATE).birthTime(BIRTH_TIME).build();
        var sajuResult = mock(SajuResult.class);

        given(sajuDataService.fetchSajuFromFastAPI(BIRTH_DATE, BIRTH_TIME)).willReturn(MOCK_SAJU);
        given(sajuAnalysisFacade.analyze(MOCK_SAJU)).willReturn(MOCK_CTX_H1);
        given(userProfileProvider.findOrCreate(BIRTH_DATE, BIRTH_TIME)).willReturn(userProfile);
        given(sajuResultMapper.buildSajuResult(any(), any(), any(), any(), any(), anyInt(), any()))
                .willReturn(sajuResult);
        given(sajuResultProvider.findOrCreate(MOCK_USER, userProfile, sajuResult)).willReturn(sajuResult);
        given(careerConsultationRepository.findBySajuResultAndConsultationMonth(any(), any()))
                .willReturn(Optional.empty());
        given(openAICaller.call(any(), any(), any(), any())).willReturn(MOCK_ADVICE);
        given(consultationSaveService.saveOrUpdate(any(), any(), any(), any()))
                .willReturn(new ConsultationSaveService.SaveOutcome(100L, true));

        ConsultationResponse result = service.getCareerConsultation(VALID_REQUEST, USER_ID);

        assertThat(result.industries()).hasSize(1);
        assertThat(result.industries().get(0).recommendedRoles()).contains("백엔드 개발자");
        assertThat(result.interviewTips()).hasSize(2);
        assertThat(result.strengths()).hasSize(2);
        assertThat(result.openaiModelVersion()).isEqualTo("gpt-4o-mini");
        assertThat(result.favoredPeriod()).isEqualTo("H1");
        assertThat(result.confidenceScore()).isEqualTo(80);
        assertThat(result.reasoning()).isNotBlank();

        assertThat(result.sajuProfile()).isNotNull();
        assertThat(result.sajuProfile().dayMaster()).isEqualTo("己");
        assertThat(result.sajuProfile().fiveElements()).containsKey("木");
        assertThat(result.sajuProfile().tenGodDistribution()).containsKey("정관");
        assertThat(result.sajuProfile().keyTenGods()).contains("정관", "편관");
        assertThat(result.cautions()).isNotEmpty();
        assertThat(result.wealthStyle()).isNotNull();
        assertThat(result.longTermRoadmap()).isNotNull();
        assertThat(result.personalBranding()).isNotNull();
        assertThat(result.powerKeywords()).isNotNull();
        assertThat(result.powerKeywords().keywords()).hasSize(1);
        assertThat(result.mentalCare()).isNotNull();
        assertThat(result.environmentFit()).isNotNull();
        assertThat(result.workStyle()).isNotNull();
        assertThat(result.relationshipStrategy()).isNotNull();
        assertThat(result.careerTimeline()).isNotNull();
        assertThat(result.careerTimeline().year()).isEqualTo(2026);
        assertThat(result.analysisSummary()).contains("己");
        assertThat(result.analysisSummary()).contains("H1");

        verify(consultationSaveService).saveOrUpdate(any(), any(), any(), any());
        verify(sajuResultProvider).findOrCreate(MOCK_USER, userProfile, sajuResult);
        // 캐시 미스 → 신규 OpenAI 호출 발생 → 일일 한도 1회 차감 필수, 성공했으므로 복원은 없어야 함
        verify(dailyApiUsageService).checkAndIncrementDailyUsage(USER_ID);
        verify(dailyApiUsageService, never()).restoreQuietly(any(), any());
        verify(dailyApiUsageService, never()).restoreQuietly(any(), any(), any());
    }

    // ─────────────────────────────────────────
    // 정상 플로우 — SajuResult 신규 생성
    // ─────────────────────────────────────────

    @Test
    @DisplayName("유효한 요청 + SajuResult 없음 → 신규 SajuResult 생성 후 컨설팅 반환")
    void shouldReturnConsultation_WhenSajuResultCreated() {
        var userProfile = UserProfile.builder().birthDate(BIRTH_DATE).birthTime(BIRTH_TIME).build();
        var newSajuResult = mock(SajuResult.class);

        given(sajuDataService.fetchSajuFromFastAPI(BIRTH_DATE, BIRTH_TIME)).willReturn(MOCK_SAJU);
        given(sajuAnalysisFacade.analyze(MOCK_SAJU)).willReturn(MOCK_CTX_H2);
        given(userProfileProvider.findOrCreate(BIRTH_DATE, BIRTH_TIME)).willReturn(userProfile);
        given(sajuResultMapper.buildSajuResult(any(), any(), any(), any(), any(), anyInt(), any()))
                .willReturn(newSajuResult);
        given(sajuResultProvider.findOrCreate(MOCK_USER, userProfile, newSajuResult)).willReturn(newSajuResult);
        given(careerConsultationRepository.findBySajuResultAndConsultationMonth(any(), any()))
                .willReturn(Optional.empty());
        given(openAICaller.call(any(), any(), any(), any())).willReturn(MOCK_ADVICE);
        given(consultationSaveService.saveOrUpdate(any(), any(), any(), any()))
                .willReturn(new ConsultationSaveService.SaveOutcome(100L, true));

        ConsultationResponse result = service.getCareerConsultation(VALID_REQUEST, USER_ID);

        assertThat(result.openaiModelVersion()).isEqualTo("gpt-4o-mini");
        assertThat(result.sajuProfile()).isNotNull();
        verify(sajuResultProvider).findOrCreate(MOCK_USER, userProfile, newSajuResult);
        verify(consultationSaveService).saveOrUpdate(any(), any(), any(), any());
        verify(dailyApiUsageService).checkAndIncrementDailyUsage(USER_ID);
        verify(dailyApiUsageService, never()).restoreQuietly(any(), any());
        verify(dailyApiUsageService, never()).restoreQuietly(any(), any(), any());
    }

    // ─────────────────────────────────────────
    // 이번 달 캐시 히트 — OpenAI 호출 없음
    // ─────────────────────────────────────────

    @Test
    @DisplayName("이번 달 캐시 히트(모델 버전 일치) → DailyApiUsage 차감 없음, OpenAI 호출 없음")
    void shouldNeverCharge_WhenCacheHit() {
        var userProfile = UserProfile.builder().birthDate(BIRTH_DATE).birthTime(BIRTH_TIME).build();
        var sajuResult = mock(SajuResult.class);
        var cachedConsultation = mock(CareerConsultation.class);

        given(sajuDataService.fetchSajuFromFastAPI(BIRTH_DATE, BIRTH_TIME)).willReturn(MOCK_SAJU);
        given(sajuAnalysisFacade.analyze(MOCK_SAJU)).willReturn(MOCK_CTX_H1);
        given(userProfileProvider.findOrCreate(BIRTH_DATE, BIRTH_TIME)).willReturn(userProfile);
        given(sajuResultMapper.buildSajuResult(any(), any(), any(), any(), any(), anyInt(), any()))
                .willReturn(sajuResult);
        given(sajuResultProvider.findOrCreate(MOCK_USER, userProfile, sajuResult)).willReturn(sajuResult);
        given(careerConsultationRepository.findBySajuResultAndConsultationMonth(any(), any()))
                .willReturn(Optional.of(cachedConsultation));
        // 모델 버전 일치 → 캐시 히트 경로 진입
        given(cachedConsultation.getOpenaiModelVersion()).willReturn("gpt-4o-mini");
        given(cachedConsultation.getId()).willReturn(99L);
        // restoreAdvice()가 호출하는 getter 설정
        given(cachedConsultation.getResultJson()).willReturn(new CareerAdviceResponse(
                List.of(), List.of(), List.of(), List.of(),
                null, null, null, null, null, null, null, null, null,
                List.of(), "", ""
        ));

        service.getCareerConsultation(VALID_REQUEST, USER_ID);

        // 캐시 히트 → OpenAI 미호출, 일일 한도 차감/복원 모두 없음
        verify(openAICaller, never()).call(any(), any(), any(), any());
        verify(dailyApiUsageService, never()).checkAndIncrementDailyUsage(any());
        verify(dailyApiUsageService, never()).restoreQuietly(any(), any());
        verify(dailyApiUsageService, never()).restoreQuietly(any(), any(), any());
    }

    // ─────────────────────────────────────────
    // 따닥(동일 요청 동시 도착) → 락 안 재확인에서 남의 결과 재사용 → 쿼터 보상
    // ─────────────────────────────────────────

    @Test
    @DisplayName("saveOrUpdate가 남의 결과를 재사용(persisted=false)했다면 예외 없이도 쿼터를 보상 복원한다")
    void shouldRestoreQuota_WhenSaveServiceReusedExistingConsultation() {
        var userProfile = UserProfile.builder().birthDate(BIRTH_DATE).birthTime(BIRTH_TIME).build();
        var sajuResult = mock(SajuResult.class);

        given(sajuDataService.fetchSajuFromFastAPI(BIRTH_DATE, BIRTH_TIME)).willReturn(MOCK_SAJU);
        given(sajuAnalysisFacade.analyze(MOCK_SAJU)).willReturn(MOCK_CTX_H1);
        given(userProfileProvider.findOrCreate(BIRTH_DATE, BIRTH_TIME)).willReturn(userProfile);
        given(sajuResultMapper.buildSajuResult(any(), any(), any(), any(), any(), anyInt(), any()))
                .willReturn(sajuResult);
        given(sajuResultProvider.findOrCreate(MOCK_USER, userProfile, sajuResult)).willReturn(sajuResult);
        given(careerConsultationRepository.findBySajuResultAndConsultationMonth(any(), any()))
                .willReturn(Optional.empty());
        given(openAICaller.call(any(), any(), any(), any())).willReturn(MOCK_ADVICE);
        // 이 스레드가 만든 advice(MOCK_ADVICE)는 버려지고, 락 안 재확인에서 이미 완료된 다른
        // 스레드가 실제로 저장한 행(WINNER_ADVICE)으로 수렴한다.
        given(consultationSaveService.saveOrUpdate(any(), any(), any(), any()))
                .willReturn(new ConsultationSaveService.SaveOutcome(77L, false));
        var winnerConsultation = mock(CareerConsultation.class);
        given(winnerConsultation.getResultJson()).willReturn(WINNER_ADVICE);
        given(careerConsultationRepository.findById(77L)).willReturn(Optional.of(winnerConsultation));

        ConsultationResponse result = service.getCareerConsultation(VALID_REQUEST, USER_ID);

        // 예외는 없었지만(정상 반환), 이 요청은 새 값을 저장하지 못했으므로 쿼터를 보상 복원해야 한다
        assertThat(result).isNotNull();
        verify(dailyApiUsageService).checkAndIncrementDailyUsage(USER_ID);
        verify(dailyApiUsageService).restoreQuietly(USER_ID, TEST_USAGE_DATE);
        // consultationId(77L)가 가리키는 실제 저장분(WINNER_ADVICE)의 내용이 응답에 실려야 한다 —
        // 이 요청이 직접 만들었다가 버려진 MOCK_ADVICE의 내용이 아니다.
        assertThat(result.strengths()).containsExactlyElementsOf(WINNER_ADVICE.strengths());
        assertThat(result.strengths()).doesNotContainAnyElementsOf(MOCK_ADVICE.strengths());
    }

    @Test
    @DisplayName("saveOrUpdate가 경합 복구까지 실패해 예외를 던지면 쿼터를 복원한 뒤 원본 예외를 전파한다")
    void shouldRestoreQuota_WhenSaveServiceFails() {
        var userProfile = UserProfile.builder().birthDate(BIRTH_DATE).birthTime(BIRTH_TIME).build();
        var sajuResult = mock(SajuResult.class);
        ConsultationRecoveryFailedException saveFailure =
                new ConsultationRecoveryFailedException("CareerConsultation 경합 복구 실패");

        given(sajuDataService.fetchSajuFromFastAPI(BIRTH_DATE, BIRTH_TIME)).willReturn(MOCK_SAJU);
        given(sajuAnalysisFacade.analyze(MOCK_SAJU)).willReturn(MOCK_CTX_H1);
        given(userProfileProvider.findOrCreate(BIRTH_DATE, BIRTH_TIME)).willReturn(userProfile);
        given(sajuResultMapper.buildSajuResult(any(), any(), any(), any(), any(), anyInt(), any()))
                .willReturn(sajuResult);
        given(sajuResultProvider.findOrCreate(MOCK_USER, userProfile, sajuResult)).willReturn(sajuResult);
        given(careerConsultationRepository.findBySajuResultAndConsultationMonth(any(), any()))
                .willReturn(Optional.empty());
        given(openAICaller.call(any(), any(), any(), any())).willReturn(MOCK_ADVICE);
        given(consultationSaveService.saveOrUpdate(any(), any(), any(), any())).willThrow(saveFailure);

        assertThatThrownBy(() -> service.getCareerConsultation(VALID_REQUEST, USER_ID))
                .isSameAs(saveFailure);

        verify(dailyApiUsageService).checkAndIncrementDailyUsage(USER_ID);
        verify(dailyApiUsageService).restoreQuietly(USER_ID, TEST_USAGE_DATE, saveFailure);
    }

    // ─────────────────────────────────────────
    // OpenAI 호출 실패
    // ─────────────────────────────────────────

    @Test
    @DisplayName("OpenAI API 호출 실패 → OpenAIApiException")
    void shouldThrow_WhenOpenAIFails() {
        var userProfile = UserProfile.builder().birthDate(BIRTH_DATE).birthTime(BIRTH_TIME).build();
        var sajuResult = mock(SajuResult.class);

        given(sajuDataService.fetchSajuFromFastAPI(BIRTH_DATE, BIRTH_TIME)).willReturn(MOCK_SAJU);
        given(sajuAnalysisFacade.analyze(MOCK_SAJU)).willReturn(MOCK_CTX_H1);
        given(userProfileProvider.findOrCreate(BIRTH_DATE, BIRTH_TIME)).willReturn(userProfile);
        given(sajuResultMapper.buildSajuResult(any(), any(), any(), any(), any(), anyInt(), any()))
                .willReturn(sajuResult);
        given(sajuResultProvider.findOrCreate(any(), any(), any())).willReturn(sajuResult);
        given(openAICaller.call(any(), any(), any(), any()))
                .willThrow(new OpenAIApiException("OpenAI API 호출 실패: connection failed"));

        assertThatThrownBy(() -> service.getCareerConsultation(VALID_REQUEST, USER_ID))
                .isInstanceOf(OpenAIApiException.class)
                .hasMessageContaining("OpenAI API 호출 실패");
        // 캐시 미스 → charge 후 OpenAI 실패: 차감 후 보상(복원)까지 발생해야 함 (US2)
        verify(dailyApiUsageService).checkAndIncrementDailyUsage(USER_ID);
        verify(dailyApiUsageService).restoreQuietly(eq(USER_ID), eq(TEST_USAGE_DATE), any());
    }

    @Test
    @DisplayName("OpenAI 응답 null → OpenAIApiException (ConsultationOpenAICaller에서 발생)")
    void shouldThrow_WhenOpenAIReturnsNull() {
        var userProfile = UserProfile.builder().birthDate(BIRTH_DATE).birthTime(BIRTH_TIME).build();
        var sajuResult = mock(SajuResult.class);

        given(sajuDataService.fetchSajuFromFastAPI(BIRTH_DATE, BIRTH_TIME)).willReturn(MOCK_SAJU);
        given(sajuAnalysisFacade.analyze(MOCK_SAJU)).willReturn(MOCK_CTX_H1);
        given(userProfileProvider.findOrCreate(BIRTH_DATE, BIRTH_TIME)).willReturn(userProfile);
        given(sajuResultMapper.buildSajuResult(any(), any(), any(), any(), any(), anyInt(), any()))
                .willReturn(sajuResult);
        given(sajuResultProvider.findOrCreate(any(), any(), any())).willReturn(sajuResult);
        given(openAICaller.call(any(), any(), any(), any()))
                .willThrow(new OpenAIApiException("OpenAI 응답이 비어있습니다"));

        assertThatThrownBy(() -> service.getCareerConsultation(VALID_REQUEST, USER_ID))
                .isInstanceOf(OpenAIApiException.class)
                .hasMessageContaining("비어있습니다");
        verify(dailyApiUsageService).checkAndIncrementDailyUsage(USER_ID);
        verify(dailyApiUsageService).restoreQuietly(eq(USER_ID), eq(TEST_USAGE_DATE), any());
    }

    @Test
    @DisplayName("OpenAI 부분 응답 (industries 빔) → OpenAIApiException (ConsultationOpenAICaller에서 발생)")
    void shouldThrow_WhenOpenAIReturnsPartialResponse_EmptyIndustries() {
        var userProfile = UserProfile.builder().birthDate(BIRTH_DATE).birthTime(BIRTH_TIME).build();
        var sajuResult = mock(SajuResult.class);

        given(sajuDataService.fetchSajuFromFastAPI(BIRTH_DATE, BIRTH_TIME)).willReturn(MOCK_SAJU);
        given(sajuAnalysisFacade.analyze(MOCK_SAJU)).willReturn(MOCK_CTX_H1);
        given(userProfileProvider.findOrCreate(BIRTH_DATE, BIRTH_TIME)).willReturn(userProfile);
        given(sajuResultMapper.buildSajuResult(any(), any(), any(), any(), any(), anyInt(), any()))
                .willReturn(sajuResult);
        given(sajuResultProvider.findOrCreate(any(), any(), any())).willReturn(sajuResult);
        given(openAICaller.call(any(), any(), any(), any()))
                .willThrow(new OpenAIApiException("산업 추천 정보가 누락되었습니다"));

        assertThatThrownBy(() -> service.getCareerConsultation(VALID_REQUEST, USER_ID))
                .isInstanceOf(OpenAIApiException.class)
                .hasMessageContaining("산업 추천 정보가 누락");
        verify(dailyApiUsageService).checkAndIncrementDailyUsage(USER_ID);
        verify(dailyApiUsageService).restoreQuietly(eq(USER_ID), eq(TEST_USAGE_DATE), any());
    }
}
