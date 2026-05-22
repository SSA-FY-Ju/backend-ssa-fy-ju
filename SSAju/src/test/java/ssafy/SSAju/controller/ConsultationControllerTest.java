package ssafy.SSAju.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ssafy.SSAju.dto.external.CareerAdviceResponse;
import ssafy.SSAju.dto.response.ConsultationResponse;
import ssafy.SSAju.exception.OpenAIApiException;
import ssafy.SSAju.handler.SajuGlobalExceptionHandler;
import ssafy.SSAju.service.ConsultationService;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConsultationController HTTP 레이어 테스트")
class ConsultationControllerTest {

    @Mock
    private ConsultationService consultationService;

    private MockMvc mockMvc;

    private static final ConsultationResponse MOCK_RESPONSE = new ConsultationResponse(
            null,
            List.of(new CareerAdviceResponse.IndustryRecommendation(
                    "금융/핀테크", "오행 金 강세", List.of("백엔드 개발자"))),
            List.of("일관성 있는 자기소개 준비", "데이터 기반 성과 강조"),
            List.of("분석력과 논리성", "책임감"),
            "gpt-4o-mini",
            "H1",
            80,
            "상반기가 취업에 유리합니다. 십신·지장간 통합 분석 기준입니다.",
            new ConsultationResponse.SajuProfile(
                    "己",
                    "己土(기토) - 수용적이고 꼼꼼한 성향",
                    Map.of("木", 1, "火", 2, "土", 2, "金", 2, "水", 1),
                    "火와 金의 기운이 강해 전략성과 실행력이 뛰어남",
                    Map.of("정관", 1, "편관", 1),
                    List.of("정관", "편관"),
                    Map.of(
                            "정관", "책임감과 원칙을 중시하며 체계적인 조직에서 두각을 나타냅니다.",
                            "편관", "강한 추진력과 도전 정신을 갖추고 있으며 목표 달성을 위해 과감하게 행동합니다."
                    )
            ),
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
                    "하나를 메인으로 선택", List.of("첫 문장 활용"), "3개 동시 사용 금지"),
            new CareerAdviceResponse.MentalCare(
                    List.of("남의 시선을 신경 쓰는 편"), List.of("혼자 산책"),
                    "완벽함은 적의다", "성과 리스트 보기"),
            new CareerAdviceResponse.EnvironmentFit(
                    "규칙과 체계가 명확한 분위기", "대기업", "시니어 상사", "객관적 논의",
                    "햇빛 드는 창가", "기술 존중 조직"),
            new CareerAdviceResponse.WorkStyle(
                    "대기업 선호", "멘토형 리더", "신중한 결정", "유연하게 대처"),
            new CareerAdviceResponse.RelationshipStrategy(
                    "조력자 스타일", "깊이 있는 관계", "go-to person", "데이터 논의", "전문가 네트워크"),
            new CareerAdviceResponse.CareerTimeline(
                    2026,
                    Map.of("March", new CareerAdviceResponse.MonthFortune("적극기", "면접 기회 많음")),
                    List.of(new CareerAdviceResponse.PivotPoint("March", "적극기", 9, "정관 기운의 절정")),
                    List.of("May", "July"),
                    "이 기간엔 급하게 결정하지 말 것"),
            "己 일간 · 오행 火·金 강세 · 정관·편관 기운 기반 | 2026년 12개월 타임라인 + 관운 분석 (H1)"
    );

    private static final String VALID_REQUEST_BODY = """
            {
              "birthDate": "1990-10-10",
              "birthTime": "14:30"
            }
            """;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ConsultationController(consultationService))
                .setControllerAdvice(new SajuGlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ─────────────────────────────────────────
    // 정상 플로우
    // ─────────────────────────────────────────

    @Test
    @DisplayName("유효한 요청 → 200 OK + 확장 컨설팅 응답")
    void shouldReturn200_WhenValidRequest() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(1L, null, List.of()));
        given(consultationService.getCareerConsultation(any(), any())).willReturn(MOCK_RESPONSE);

        mockMvc.perform(post("/api/career/consultation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.industries").isArray())
                .andExpect(jsonPath("$.data.industries[0].recommendedRoles").isArray())
                .andExpect(jsonPath("$.data.interviewTips").isArray())
                .andExpect(jsonPath("$.data.strengths").isArray())
                .andExpect(jsonPath("$.data.openaiModelVersion").value("gpt-4o-mini"))
                .andExpect(jsonPath("$.data.favoredPeriod").value("H1"))
                .andExpect(jsonPath("$.data.confidenceScore").value(80))
                .andExpect(jsonPath("$.data.sajuProfile").exists())
                .andExpect(jsonPath("$.data.sajuProfile.dayMaster").value("己"))
                .andExpect(jsonPath("$.data.sajuProfile.fiveElements").exists())
                .andExpect(jsonPath("$.data.cautions").isArray())
                .andExpect(jsonPath("$.data.wealthStyle").exists())
                .andExpect(jsonPath("$.data.longTermRoadmap").exists())
                .andExpect(jsonPath("$.data.personalBranding").exists())
                .andExpect(jsonPath("$.data.powerKeywords").exists())
                .andExpect(jsonPath("$.data.powerKeywords.keywords").isArray())
                .andExpect(jsonPath("$.data.mentalCare").exists())
                .andExpect(jsonPath("$.data.environmentFit").exists())
                .andExpect(jsonPath("$.data.workStyle").exists())
                .andExpect(jsonPath("$.data.relationshipStrategy").exists())
                .andExpect(jsonPath("$.data.careerTimeline").exists())
                .andExpect(jsonPath("$.data.careerTimeline.year").value(2026))
                .andExpect(jsonPath("$.data.analysisSummary").isNotEmpty());
    }

    // ─────────────────────────────────────────
    // 입력값 검증 실패
    // ─────────────────────────────────────────

    @Test
    @DisplayName("birthTime 누락 → 400 Bad Request")
    void shouldReturn400_WhenBirthTimeMissing() throws Exception {
        mockMvc.perform(post("/api/career/consultation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "birthDate": "1990-10-10"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("빈 요청 바디 → 400 Bad Request")
    void shouldReturn400_WhenEmptyBody() throws Exception {
        mockMvc.perform(post("/api/career/consultation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────────────────────────
    // 서비스 예외 전파
    // ─────────────────────────────────────────

    @Test
    @DisplayName("OpenAI 타임아웃 → 504 Gateway Timeout")
    void shouldReturn504_WhenOpenAITimeout() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(1L, null, List.of()));
        given(consultationService.getCareerConsultation(any(), any()))
                .willThrow(new OpenAIApiException("OpenAI API 요청 시간 초과"));

        mockMvc.perform(post("/api/career/consultation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST_BODY))
                .andExpect(status().isGatewayTimeout());
    }
}
