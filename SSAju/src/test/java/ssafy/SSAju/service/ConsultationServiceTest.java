package ssafy.SSAju.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ssafy.SSAju.career.caller.ConsultationOpenAICaller;
import ssafy.SSAju.career.domain.HiddenStems;
import ssafy.SSAju.career.domain.TenGodDistribution;
import ssafy.SSAju.career.entity.CareerConsultation;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.mapper.ConsultationMapper;
import ssafy.SSAju.career.mapper.SajuResultMapper;
import ssafy.SSAju.career.provider.SajuResultProvider;
import ssafy.SSAju.career.provider.UserProfileProvider;
import ssafy.SSAju.career.util.CareerFortuneAnalyzer;
import ssafy.SSAju.career.util.HiddenStemCalculator;
import ssafy.SSAju.career.util.TenGodCalculator;
import ssafy.SSAju.career.validator.SajuValidator;
import ssafy.SSAju.dto.external.CareerAdviceResponse;
import ssafy.SSAju.dto.external.FastAPIResponse;
import ssafy.SSAju.dto.request.ConsultationRequest;
import ssafy.SSAju.dto.response.ConsultationResponse;
import ssafy.SSAju.exception.OpenAIApiException;
import ssafy.SSAju.repository.CareerConsultationRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConsultationService 단위 테스트")
class ConsultationServiceTest {

    @Mock private ConsultationOpenAICaller openAICaller;
    @Mock private SajuDataService sajuDataService;
    @Mock private TenGodCalculator tenGodCalculator;
    @Mock private HiddenStemCalculator hiddenStemCalculator;
    @Mock private CareerFortuneAnalyzer careerFortuneAnalyzer;
    @Mock private UserProfileProvider userProfileProvider;
    @Mock private SajuResultProvider sajuResultProvider;
    @Mock private SajuResultMapper sajuResultMapper;
    @Mock private ConsultationMapper consultationMapper;
    @Mock private CareerConsultationRepository careerConsultationRepository;

    private ConsultationService service;

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
                    Map.of("March", new CareerAdviceResponse.MonthFortune("적극기", "면접 기회 많음")),
                    List.of(new CareerAdviceResponse.PivotPoint("March", "적극기", 9, "정관 기운의 절정")),
                    List.of("May", "July"),
                    "이 기간엔 급하게 결정하지 말 것"),
            List.of("정관", "편관"),
            "己土(기토) - 수용적이고 꼼꼼한 성향",
            "火와 金의 기운이 강해 전략성과 실행력이 뛰어남"
    );

    @BeforeEach
    void setUp() {
        service = new ConsultationService(
                openAICaller, sajuDataService, tenGodCalculator, hiddenStemCalculator, careerFortuneAnalyzer,
                userProfileProvider, sajuResultProvider, sajuResultMapper, consultationMapper,
                careerConsultationRepository, new SajuValidator());
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
        var consultation = mock(CareerConsultation.class);

        given(sajuDataService.fetchSajuFromFastAPI(BIRTH_DATE, BIRTH_TIME)).willReturn(MOCK_SAJU);
        given(tenGodCalculator.calculate(MOCK_SAJU.heavenlyStems())).willReturn(TEN_GOD);
        given(hiddenStemCalculator.calculate(MOCK_SAJU.earthlyBranches())).willReturn(HIDDEN_STEMS);
        given(careerFortuneAnalyzer.analyzeFavoredPeriod(any(), any(), any(), any())).willReturn("H1");
        given(careerFortuneAnalyzer.calculateConfidenceScore(any(), any(), any())).willReturn(80);
        given(careerFortuneAnalyzer.buildReasoning(anyString(), any())).willReturn("상반기가 취업에 유리합니다.");
        given(userProfileProvider.findOrCreate(BIRTH_DATE, BIRTH_TIME)).willReturn(userProfile);
        given(sajuResultMapper.buildSajuResult(any(), any(), any(), any(), any(), anyInt(), any()))
                .willReturn(sajuResult);
        given(sajuResultProvider.findOrCreate(userProfile, sajuResult)).willReturn(sajuResult);
        given(openAICaller.call(any(), any(), any(), any())).willReturn(MOCK_ADVICE);
        given(consultationMapper.buildConsultation(any(), any(), any())).willReturn(consultation);
        given(careerConsultationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(consultationMapper.buildAnalysisSummary(any(), any(), any(), any()))
                .willReturn("己 일간 · 오행 火·金 강세 · 정관·편관 기운 기반 | 2026년 12개월 타임라인 + 관운 분석 (H1)");

        ConsultationResponse result = service.getCareerConsultation(VALID_REQUEST);

        // 기존 필드 검증
        assertThat(result.industries()).hasSize(1);
        assertThat(result.industries().get(0).recommendedRoles()).contains("백엔드 개발자");
        assertThat(result.interviewTips()).hasSize(2);
        assertThat(result.strengths()).hasSize(2);
        assertThat(result.openaiModelVersion()).isEqualTo("gpt-4o-mini");
        assertThat(result.favoredPeriod()).isEqualTo("H1");
        assertThat(result.confidenceScore()).isEqualTo(80);
        assertThat(result.reasoning()).isNotBlank();

        // 신규 필드 검증
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
        assertThat(result.analysisSummary()).isNotBlank();
        assertThat(result.analysisSummary()).contains("己");
        assertThat(result.analysisSummary()).contains("H1");

        verify(careerConsultationRepository).save(any());
        verify(sajuResultProvider).findOrCreate(userProfile, sajuResult);
    }

    // ─────────────────────────────────────────
    // 정상 플로우 — SajuResult 신규 생성
    // ─────────────────────────────────────────

    @Test
    @DisplayName("유효한 요청 + SajuResult 없음 → 신규 SajuResult 생성 후 컨설팅 반환")
    void shouldReturnConsultation_WhenSajuResultCreated() {
        var userProfile = UserProfile.builder().birthDate(BIRTH_DATE).birthTime(BIRTH_TIME).build();
        var newSajuResult = mock(SajuResult.class);
        var consultation = mock(CareerConsultation.class);

        given(sajuDataService.fetchSajuFromFastAPI(BIRTH_DATE, BIRTH_TIME)).willReturn(MOCK_SAJU);
        given(tenGodCalculator.calculate(MOCK_SAJU.heavenlyStems())).willReturn(TEN_GOD);
        given(hiddenStemCalculator.calculate(MOCK_SAJU.earthlyBranches())).willReturn(HIDDEN_STEMS);
        given(careerFortuneAnalyzer.analyzeFavoredPeriod(any(), any(), any(), any())).willReturn("H2");
        given(careerFortuneAnalyzer.calculateConfidenceScore(any(), any(), any())).willReturn(60);
        given(careerFortuneAnalyzer.buildReasoning(anyString(), any())).willReturn("하반기가 취업에 유리합니다.");
        given(userProfileProvider.findOrCreate(BIRTH_DATE, BIRTH_TIME)).willReturn(userProfile);
        given(sajuResultMapper.buildSajuResult(any(), any(), any(), any(), any(), anyInt(), any()))
                .willReturn(newSajuResult);
        given(sajuResultProvider.findOrCreate(userProfile, newSajuResult)).willReturn(newSajuResult);
        given(openAICaller.call(any(), any(), any(), any())).willReturn(MOCK_ADVICE);
        given(consultationMapper.buildConsultation(any(), any(), any())).willReturn(consultation);
        given(careerConsultationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(consultationMapper.buildAnalysisSummary(any(), any(), any(), any()))
                .willReturn("己 일간 · 오행 火·金 강세 | H2");

        ConsultationResponse result = service.getCareerConsultation(VALID_REQUEST);

        assertThat(result.openaiModelVersion()).isEqualTo("gpt-4o-mini");
        assertThat(result.sajuProfile()).isNotNull();
        verify(sajuResultProvider).findOrCreate(userProfile, newSajuResult);
        verify(careerConsultationRepository).save(any());
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
        given(tenGodCalculator.calculate(any())).willReturn(TEN_GOD);
        given(hiddenStemCalculator.calculate(any())).willReturn(HIDDEN_STEMS);
        given(careerFortuneAnalyzer.analyzeFavoredPeriod(any(), any(), any(), any())).willReturn("H1");
        given(careerFortuneAnalyzer.calculateConfidenceScore(any(), any(), any())).willReturn(70);
        given(careerFortuneAnalyzer.buildReasoning(anyString(), any())).willReturn("상반기가 취업에 유리합니다.");
        given(userProfileProvider.findOrCreate(BIRTH_DATE, BIRTH_TIME)).willReturn(userProfile);
        given(sajuResultMapper.buildSajuResult(any(), any(), any(), any(), any(), anyInt(), any()))
                .willReturn(sajuResult);
        given(sajuResultProvider.findOrCreate(any(), any())).willReturn(sajuResult);
        given(openAICaller.call(any(), any(), any(), any()))
                .willThrow(new OpenAIApiException("OpenAI API 호출 실패: connection failed"));

        assertThatThrownBy(() -> service.getCareerConsultation(VALID_REQUEST))
                .isInstanceOf(OpenAIApiException.class)
                .hasMessageContaining("OpenAI API 호출 실패");
    }

    // ─────────────────────────────────────────
    // OpenAI 응답 null
    // ─────────────────────────────────────────

    @Test
    @DisplayName("OpenAI 응답 null → OpenAIApiException (ConsultationOpenAICaller에서 발생)")
    void shouldThrow_WhenOpenAIReturnsNull() {
        var userProfile = UserProfile.builder().birthDate(BIRTH_DATE).birthTime(BIRTH_TIME).build();
        var sajuResult = mock(SajuResult.class);

        given(sajuDataService.fetchSajuFromFastAPI(BIRTH_DATE, BIRTH_TIME)).willReturn(MOCK_SAJU);
        given(tenGodCalculator.calculate(any())).willReturn(TEN_GOD);
        given(hiddenStemCalculator.calculate(any())).willReturn(HIDDEN_STEMS);
        given(careerFortuneAnalyzer.analyzeFavoredPeriod(any(), any(), any(), any())).willReturn("H1");
        given(careerFortuneAnalyzer.calculateConfidenceScore(any(), any(), any())).willReturn(70);
        given(careerFortuneAnalyzer.buildReasoning(anyString(), any())).willReturn("상반기가 취업에 유리합니다.");
        given(userProfileProvider.findOrCreate(BIRTH_DATE, BIRTH_TIME)).willReturn(userProfile);
        given(sajuResultMapper.buildSajuResult(any(), any(), any(), any(), any(), anyInt(), any()))
                .willReturn(sajuResult);
        given(sajuResultProvider.findOrCreate(any(), any())).willReturn(sajuResult);
        given(openAICaller.call(any(), any(), any(), any()))
                .willThrow(new OpenAIApiException("OpenAI 응답이 비어있습니다"));

        assertThatThrownBy(() -> service.getCareerConsultation(VALID_REQUEST))
                .isInstanceOf(OpenAIApiException.class)
                .hasMessageContaining("비어있습니다");
    }

    @Test
    @DisplayName("OpenAI 부분 응답 (industries 빔) → OpenAIApiException (ConsultationOpenAICaller에서 발생)")
    void shouldThrow_WhenOpenAIReturnsPartialResponse_EmptyIndustries() {
        var userProfile = UserProfile.builder().birthDate(BIRTH_DATE).birthTime(BIRTH_TIME).build();
        var sajuResult = mock(SajuResult.class);

        given(sajuDataService.fetchSajuFromFastAPI(BIRTH_DATE, BIRTH_TIME)).willReturn(MOCK_SAJU);
        given(tenGodCalculator.calculate(any())).willReturn(TEN_GOD);
        given(hiddenStemCalculator.calculate(any())).willReturn(HIDDEN_STEMS);
        given(careerFortuneAnalyzer.analyzeFavoredPeriod(any(), any(), any(), any())).willReturn("H1");
        given(careerFortuneAnalyzer.calculateConfidenceScore(any(), any(), any())).willReturn(70);
        given(careerFortuneAnalyzer.buildReasoning(anyString(), any())).willReturn("상반기가 취업에 유리합니다.");
        given(userProfileProvider.findOrCreate(BIRTH_DATE, BIRTH_TIME)).willReturn(userProfile);
        given(sajuResultMapper.buildSajuResult(any(), any(), any(), any(), any(), anyInt(), any()))
                .willReturn(sajuResult);
        given(sajuResultProvider.findOrCreate(any(), any())).willReturn(sajuResult);
        given(openAICaller.call(any(), any(), any(), any()))
                .willThrow(new OpenAIApiException("산업 추천 정보가 누락되었습니다"));

        assertThatThrownBy(() -> service.getCareerConsultation(VALID_REQUEST))
                .isInstanceOf(OpenAIApiException.class)
                .hasMessageContaining("산업 추천 정보가 누락");
    }
}
