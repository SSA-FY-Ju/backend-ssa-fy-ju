package ssafy.SSAju.career.strategy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ssafy.SSAju.career.caller.ConsultationOpenAICaller;
import ssafy.SSAju.career.domain.AnalysisPayload;
import ssafy.SSAju.career.domain.AnalysisTimeContext;
import ssafy.SSAju.career.enums.AnalysisType;
import ssafy.SSAju.career.mapper.ConsultationMapper;
import ssafy.SSAju.career.provider.SajuAnalysisFacade;
import ssafy.SSAju.career.util.CareerFortuneAnalyzer;
import ssafy.SSAju.career.util.HiddenStemCalculator;
import ssafy.SSAju.career.util.TenGodCalculator;
import ssafy.SSAju.career.validator.RequestValidator;
import ssafy.SSAju.dto.external.CareerAdviceResponse;
import ssafy.SSAju.dto.external.FastAPIResponse;
import ssafy.SSAju.dto.request.ConsultationRequest;
import ssafy.SSAju.exception.InvalidSajuDataException;
import ssafy.SSAju.exception.OpenAIApiException;
import ssafy.SSAju.integration.UnifiedAnalysisTestSupport;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConsultationAnalysisStrategy 단위 테스트")
class ConsultationAnalysisStrategyTest {

    private static final LocalDate BIRTH_DATE = LocalDate.of(1995, 3, 20);
    private static final LocalTime BIRTH_TIME = LocalTime.of(9, 15);
    private static final String MODEL_VERSION = "gpt-4o-mini";

    /** ConsultationMapper의 Clock(2026년)과 다른 연도를 써서 요약이 시간 문맥을 따르는지 확인한다. */
    private static final AnalysisTimeContext TIME_CONTEXT = new AnalysisTimeContext(
            LocalDate.of(2027, 4, 15), 2027, 4, List.of(4, 5, 6, 7, 8));

    @Mock private ConsultationOpenAICaller openAICaller;

    private final TenGodCalculator tenGodCalculator = new TenGodCalculator();
    private final SajuAnalysisFacade facade = new SajuAnalysisFacade(
            tenGodCalculator, new HiddenStemCalculator(), new CareerFortuneAnalyzer(tenGodCalculator));
    private final ConsultationMapper consultationMapper =
            new ConsultationMapper(UnifiedAnalysisTestSupport.fixedClock());

    private ConsultationAnalysisStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new ConsultationAnalysisStrategy(
                facade, openAICaller, consultationMapper, new RequestValidator());
        ReflectionTestUtils.setField(strategy, "modelVersion", MODEL_VERSION);
    }

    @Test
    @DisplayName("커리어 컨설팅 유형만 지원한다")
    void shouldSupportOnlyConsultationType() {
        assertThat(strategy.supports(AnalysisType.CAREER_CONSULTATION)).isTrue();
        assertThat(strategy.supports(AnalysisType.SAJU)).isFalse();
        assertThat(strategy.supports(AnalysisType.COMPANY_COMPATIBILITY)).isFalse();
    }

    @Test
    @DisplayName("저장된 만세력으로 OpenAI를 호출하고 완성 스냅샷을 만든다")
    void shouldBuildCompletedSnapshot() {
        // Given
        given(openAICaller.call(any(), any(), any(), eq("己"), any())).willReturn(advice());

        // When
        AnalysisOutcome<AnalysisPayload.ConsultationResultData> outcome = strategy.analyze(
                new ConsultationRequest(BIRTH_DATE, BIRTH_TIME),
                UnifiedAnalysisTestSupport.manseData(),
                TIME_CONTEXT);

        // Then
        AnalysisPayload.ConsultationResultData result = outcome.result();
        assertThat(result.openaiModelVersion()).isEqualTo(MODEL_VERSION);
        assertThat(result.industries()).hasSize(1);
        assertThat(result.sajuProfile().dayMaster()).isEqualTo("己");
        assertThat(result.sajuProfile().fiveElements()).containsEntry("火", 3);
        assertThat(result.sajuProfile().keyTenGods()).containsExactly("정관");
        assertThat(result.favoredPeriod()).isIn("H1", "H2");
        assertThat(result.careerTimeline()).isNotNull();

        AnalysisPayload.ConsultationInput input = (AnalysisPayload.ConsultationInput) outcome.input();
        assertThat(input.birthDate()).isEqualTo(BIRTH_DATE);
        assertThat(input.consultationMonth()).isEqualTo(202704);
        assertThat(input.analysisYear()).isEqualTo(2027);
    }

    @Test
    @DisplayName("요약은 현재 연도가 아니라 요청 시작 시 확정된 연도를 따른다")
    void shouldBuildSummaryWithRequestYear() {
        // Given
        given(openAICaller.call(any(), any(), any(), any(), any())).willReturn(advice());

        // When
        AnalysisOutcome<AnalysisPayload.ConsultationResultData> outcome = strategy.analyze(
                new ConsultationRequest(BIRTH_DATE, BIRTH_TIME),
                UnifiedAnalysisTestSupport.manseData(),
                TIME_CONTEXT);

        // Then
        assertThat(outcome.result().analysisSummary()).contains("2027년").doesNotContain("2026년");
    }

    @Test
    @DisplayName("OpenAI에 전달하는 사주 데이터는 저장된 만세력에서 복원한다")
    void shouldPassStoredManseToOpenAI() {
        // Given
        given(openAICaller.call(any(), any(), any(), any(), any())).willReturn(advice());

        // When
        strategy.analyze(new ConsultationRequest(BIRTH_DATE, BIRTH_TIME),
                UnifiedAnalysisTestSupport.manseData(), TIME_CONTEXT);

        // Then
        org.mockito.ArgumentCaptor<FastAPIResponse> captor =
                org.mockito.ArgumentCaptor.forClass(FastAPIResponse.class);
        verify(openAICaller).call(captor.capture(), any(), any(), any(), any());
        FastAPIResponse passed = captor.getValue();
        assertThat(passed.dayPillar()).isEqualTo("己未");
        assertThat(passed.birthDate()).isEqualTo(BIRTH_DATE.toString());
        assertThat(passed.birthTime()).isEqualTo(BIRTH_TIME.toString());
    }

    @Test
    @DisplayName("OpenAI 호출 실패는 그대로 전파되어 결과를 만들지 않는다")
    void shouldPropagateOpenAIFailure() {
        // Given
        willThrow(new OpenAIApiException("OpenAI 실패"))
                .given(openAICaller).call(any(), any(), any(), any(), any());

        // When / Then
        assertThatThrownBy(() -> strategy.analyze(
                new ConsultationRequest(BIRTH_DATE, BIRTH_TIME),
                UnifiedAnalysisTestSupport.manseData(), TIME_CONTEXT))
                .isInstanceOf(OpenAIApiException.class);
    }

    @Test
    @DisplayName("필수 항목이 빠진 결과는 저장 전에 거부된다")
    void shouldRejectIncompleteResult() {
        // Given
        given(openAICaller.call(any(), any(), any(), any(), any())).willReturn(advice());
        AnalysisPayload.ConsultationResultData valid = strategy.analyze(
                new ConsultationRequest(BIRTH_DATE, BIRTH_TIME),
                UnifiedAnalysisTestSupport.manseData(), TIME_CONTEXT).result();

        AnalysisPayload.ConsultationResultData missingSummary = new AnalysisPayload.ConsultationResultData(
                AnalysisPayload.SCHEMA_VERSION, valid.industries(), valid.interviewTips(), valid.strengths(),
                valid.openaiModelVersion(), valid.favoredPeriod(), valid.confidenceScore(), valid.reasoning(),
                valid.sajuProfile(), valid.cautions(), valid.wealthStyle(), valid.longTermRoadmap(),
                valid.personalBranding(), valid.powerKeywords(), valid.mentalCare(), valid.environmentFit(),
                valid.workStyle(), valid.relationshipStrategy(), valid.careerTimeline(), "  ");

        // When / Then
        strategy.validateResult(valid);
        assertThatThrownBy(() -> strategy.validateResult(missingSummary))
                .isInstanceOf(InvalidSajuDataException.class);
    }

    private CareerAdviceResponse advice() {
        return new CareerAdviceResponse(
                List.of(new CareerAdviceResponse.IndustryRecommendation(
                        "IT/소프트웨어", "논리적 설계 성향", List.of("백엔드 개발"))),
                List.of("성과를 수치로 설명하세요"),
                List.of("끈기"),
                List.of("과로 주의"),
                new CareerAdviceResponse.WealthStyle("근로소득", "고정지출 축소", "안정 지향", "기술 강의"),
                new CareerAdviceResponse.LongTermRoadmap(
                        new CareerAdviceResponse.PhaseAdvice("실무 역량", "핵심 기술", "사이드 프로젝트"),
                        new CareerAdviceResponse.PhaseAdvice("리드 경험", "설계", "팀 과제"),
                        "기술 리더", "설계와 조직을 함께"),
                new CareerAdviceResponse.PersonalBranding("네이비", "차분함", "단정", "안정적 실행가", "책임지는 개발자"),
                new CareerAdviceResponse.PowerKeywords(
                        List.of(new CareerAdviceResponse.PowerKeyword("책임감", "土", "끝까지 수행", "장애 대응 주도", "면접")),
                        "한 가지를 반복", List.of("첫 문장에 배치"), "추상적 수식어 금지"),
                new CareerAdviceResponse.MentalCare(List.of("과도한 책임"), List.of("산책"), "하나씩", "10분 휴식"),
                new CareerAdviceResponse.EnvironmentFit("집중", "중견 이상", "논리적", "사실 중심", "독립 좌석", "문서화"),
                new CareerAdviceResponse.WorkStyle("기술 중심", "조율형", "데이터 기반", "중재"),
                new CareerAdviceResponse.RelationshipStrategy("소수 관계", "스터디", "조율자", "사실 확인", "커뮤니티"),
                new CareerAdviceResponse.CareerTimeline(
                        2027,
                        java.util.Map.of(1, new CareerAdviceResponse.MonthFortune("상승", "새 과제")),
                        List.of(new CareerAdviceResponse.PivotPoint(5, "전환", 80, "이직 제안")),
                        List.of(8), "8월 확장 주의"),
                List.of("정관"),
                "포용력 있는 일간",
                "화와 토가 강함");
    }
}
