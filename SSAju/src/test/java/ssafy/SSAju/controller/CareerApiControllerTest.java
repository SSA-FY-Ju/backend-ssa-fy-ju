package ssafy.SSAju.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import ssafy.SSAju.career.enums.ForecastStatus;
import ssafy.SSAju.career.util.JobCategoryEnum;
import ssafy.SSAju.dto.external.CareerAdviceResponse;
import ssafy.SSAju.dto.response.CareerTimingResponse;
import ssafy.SSAju.dto.response.CompatibilityResponse;
import ssafy.SSAju.dto.response.ConsultationResponse;
import ssafy.SSAju.dto.response.SatisfactionFeedbackResponse;
import ssafy.SSAju.service.CareerFortuneService;
import ssafy.SSAju.service.CompanyMatchingService;
import ssafy.SSAju.service.ConsultationService;
import ssafy.SSAju.service.FeedbackService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DisplayName("Career API 컨트롤러 테스트 (T089) - 직렬화·검증·인증 검증")
class CareerApiControllerTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @MockitoBean
    private CareerFortuneService careerFortuneService;

    @MockitoBean
    private ConsultationService consultationService;

    @MockitoBean
    private CompanyMatchingService companyMatchingService;

    @MockitoBean
    private FeedbackService feedbackService;

    @MockitoBean
    private ssafy.SSAju.service.DailyApiUsageService dailyApiUsageService;

    private static final UsernamePasswordAuthenticationToken AUTH_TOKEN =
            new UsernamePasswordAuthenticationToken(1L, null, List.of());

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    // ─────────────────────────────────────────────────────────────────
    // US1: Career Timing Analysis
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("T089-1: POST /api/career/timing - 유효한 요청 → H1/H2 판정 응답")
    void us1_careerTiming_validRequest_returnsH1orH2() throws Exception {
        // Given
        given(careerFortuneService.analyzeCareerTiming(any(), any(), any()))
                .willReturn(new CareerTimingResponse(null, "H1", 75, "상반기가 취업에 유리합니다."));

        // When & Then
        mockMvc.perform(post("/api/career/timing")
                        .with(authentication(AUTH_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"birthDate": "1990-10-10", "birthTime": "14:30"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.favoredPeriod").value("H1"))
                .andExpect(jsonPath("$.data.confidenceScore").value(75))
                .andExpect(jsonPath("$.data.reasoning").isNotEmpty());
    }

    @Test
    @DisplayName("T089-1a: POST /api/career/timing - birthTime 누락 → 400 Bad Request")
    void us1_careerTiming_missingBirthTime_returns400() throws Exception {
        mockMvc.perform(post("/api/career/timing")
                        .with(authentication(AUTH_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"birthDate": "1990-10-10"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    // ─────────────────────────────────────────────────────────────────
    // US2: AI Consultation
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("T089-2: POST /api/career/consultation - 유효한 요청 → 23개 필드 응답")
    void us2_consultation_validRequest_returns23Fields() throws Exception {
        // Given
        given(consultationService.getCareerConsultation(any(), any()))
                .willReturn(buildMockConsultationResponse());

        // When & Then
        mockMvc.perform(post("/api/career/consultation")
                        .with(authentication(AUTH_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"birthDate": "1990-10-10", "birthTime": "14:30"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                // 기본 조언 3필드
                .andExpect(jsonPath("$.data.industries").isArray())
                .andExpect(jsonPath("$.data.interviewTips").isArray())
                .andExpect(jsonPath("$.data.strengths").isArray())
                // 관운 분석 3필드
                .andExpect(jsonPath("$.data.favoredPeriod").value("H1"))
                .andExpect(jsonPath("$.data.confidenceScore").value(80))
                .andExpect(jsonPath("$.data.reasoning").isNotEmpty())
                // 사주 프로필
                .andExpect(jsonPath("$.data.sajuProfile.dayMaster").value("己"))
                .andExpect(jsonPath("$.data.sajuProfile.dayMasterDescription").isNotEmpty())
                .andExpect(jsonPath("$.data.sajuProfile.fiveElements").isMap())
                .andExpect(jsonPath("$.data.sajuProfile.tenGodDistribution").isMap())
                .andExpect(jsonPath("$.data.sajuProfile.keyTenGods").isArray())
                // OpenAI 분석 10필드
                .andExpect(jsonPath("$.data.cautions").isArray())
                .andExpect(jsonPath("$.data.wealthStyle").exists())
                .andExpect(jsonPath("$.data.longTermRoadmap").exists())
                .andExpect(jsonPath("$.data.personalBranding").exists())
                .andExpect(jsonPath("$.data.powerKeywords").exists())
                .andExpect(jsonPath("$.data.mentalCare").exists())
                .andExpect(jsonPath("$.data.environmentFit").exists())
                .andExpect(jsonPath("$.data.workStyle").exists())
                .andExpect(jsonPath("$.data.relationshipStrategy").exists())
                .andExpect(jsonPath("$.data.careerTimeline").exists());
    }

    @Test
    @DisplayName("T089-2a: POST /api/career/consultation - birthTime 누락 → 400 Bad Request")
    void us2_consultation_missingBirthTime_returns400() throws Exception {
        mockMvc.perform(post("/api/career/consultation")
                        .with(authentication(AUTH_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"birthDate": "1990-10-10"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    // ─────────────────────────────────────────────────────────────────
    // US4: Feedback
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("T089-3: POST /api/feedback/satisfaction - 유효한 피드백 → feedbackId 반환")
    void us4_feedback_validRequest_returnsFeedbackId() throws Exception {
        // Given
        given(feedbackService.saveFeedback(any(), anyLong()))
                .willReturn(new SatisfactionFeedbackResponse(1L, LocalDateTime.now(), "좋은 상담이었습니다"));

        // When & Then
        mockMvc.perform(post("/api/feedback/satisfaction")
                        .with(authentication(AUTH_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "analysisId": 1,
                                  "feedbackType": "CONSULTATION",
                                  "satisfactionStatus": "SATISFIED",
                                  "feedbackContent": "좋은 상담이었습니다"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.feedbackId").value(1))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.feedbackContent").value("좋은 상담이었습니다"));
    }

    @Test
    @DisplayName("T089-3a: POST /api/feedback/satisfaction - analysisId 누락 → 400 Bad Request")
    void us4_feedback_missingAnalysisId_returns400() throws Exception {
        mockMvc.perform(post("/api/feedback/satisfaction")
                        .with(authentication(AUTH_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "feedbackType": "CONSULTATION",
                                  "satisfactionStatus": "SATISFIED"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    // ─────────────────────────────────────────────────────────────────
    // US3: Company Compatibility
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("T089-4: POST /api/company/compatibility - 유효한 요청 → 호환성 점수 반환")
    void us3_compatibility_validRequest_returnsScore() throws Exception {
        // Given
        given(companyMatchingService.analyzeCompatibility(any(), any()))
                .willReturn(buildMockCompatibilityResponse());

        // When & Then
        mockMvc.perform(post("/api/company/compatibility")
                        .with(authentication(AUTH_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userBirthDate": "1990-10-10",
                                  "userBirthTime": "14:30",
                                  "targetRole": {
                                    "category": "TECH_BACKEND",
                                    "detailName": "Spring Boot 개발자"
                                  },
                                  "companyName": "Samsung",
                                  "companyFoundingDate": "1938-01-13"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.compatibilityScore").value(78))
                .andExpect(jsonPath("$.data.requestContext.companyName").value("Samsung"))
                .andExpect(jsonPath("$.data.summary").isNotEmpty())
                .andExpect(jsonPath("$.data.roleCompatibility").isArray())
                .andExpect(jsonPath("$.data.monthlyForecast").isArray());
    }

    @Test
    @DisplayName("T089-4a: POST /api/company/compatibility - companyName 누락 → 400 Bad Request")
    void us3_compatibility_missingCompanyName_returns400() throws Exception {
        mockMvc.perform(post("/api/company/compatibility")
                        .with(authentication(AUTH_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userBirthDate": "1990-10-10",
                                  "userBirthTime": "14:30",
                                  "targetRole": {
                                    "category": "TECH_BACKEND"
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ─────────────────────────────────────────────────────────────────
    // 전체 흐름 - 공통 에러 처리 검증
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("T089-5: 빈 요청 바디 → 4개 엔드포인트 모두 400 Bad Request")
    void allEndpoints_emptyBody_returns400() throws Exception {
        mockMvc.perform(post("/api/career/timing")
                        .with(authentication(AUTH_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/career/consultation")
                        .with(authentication(AUTH_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/feedback/satisfaction")
                        .with(authentication(AUTH_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/company/compatibility")
                        .with(authentication(AUTH_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────────────────────────────────────────────────
    // Swagger 인증 보호 검증
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("T089-6: Swagger API Docs는 인증 없이 접근 가능 (Nginx IP 화이트리스트로 보호)")
    void swaggerDocs_publiclyAccessible() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("T089-6a: Swagger YAML Docs도 인증 없이 접근 가능")
    void swaggerYamlDocs_publiclyAccessible() throws Exception {
        mockMvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("T089-7: 미인증 사용자의 API 엔드포인트 요청 → 401 Unauthorized")
    void apiEndpoints_withoutAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/career/timing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"birthDate": "1990-10-10", "birthTime": "14:30"}
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/career/consultation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"birthDate": "1990-10-10", "birthTime": "14:30"}
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/company/compatibility")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userBirthDate": "1990-10-10",
                                  "userBirthTime": "14:30",
                                  "targetRole": { "category": "TECH_BACKEND" },
                                  "companyName": "Samsung",
                                  "companyFoundingDate": "1938-01-13"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    // ─────────────────────────────────────────────────────────────────
    // Mock 데이터 헬퍼
    // ─────────────────────────────────────────────────────────────────

    private ConsultationResponse buildMockConsultationResponse() {
        return new ConsultationResponse(
                null,
                null,
                List.of(new CareerAdviceResponse.IndustryRecommendation(
                        "금융/핀테크", "오행 金 강세", List.of("백엔드 개발자"))),
                List.of("일관성 있는 자기소개 준비"),
                List.of("분석력과 논리성"),
                "gpt-4o-mini",
                "H1", 80,
                "상반기가 취업에 유리합니다.",
                new ConsultationResponse.SajuProfile(
                        "己", "己土(기토) - 수용적이고 꼼꼼한 성향",
                        Map.of("木", 1, "火", 2, "土", 2, "金", 2, "水", 1),
                        "火와 金의 기운이 강해 전략성이 뛰어남",
                        Map.of("정관", 1, "편관", 1),
                        List.of("정관", "편관"),
                        Map.of("정관", "책임감과 원칙을 중시합니다.", "편관", "강한 추진력을 갖습니다.")
                ),
                List.of("지나친 꼼꼼함 주의"),
                new CareerAdviceResponse.WealthStyle(
                        "안정적인 월급", "기술 전문성 향상", "보수적 투자", "기술 블로그"),
                new CareerAdviceResponse.LongTermRoadmap(
                        new CareerAdviceResponse.PhaseAdvice("기본기 다지기", "백엔드 심화", "오픈소스 기여"),
                        new CareerAdviceResponse.PhaseAdvice("시니어 전환", "팀리드", "아키텍처"),
                        "CTO", "정관 기운으로 기술 방향 주도"),
                new CareerAdviceResponse.PersonalBranding(
                        "네이비 수트", "신뢰감", "정돈된 스타일", "책임감 엔지니어", "안정과 혁신"),
                new CareerAdviceResponse.PowerKeywords(
                        List.of(new CareerAdviceResponse.PowerKeyword(
                                "뿌리깊은_책임감", "土", "책임감 성향", "책임감 있는 개발자", "자소서")),
                        "하나 선택", List.of("첫 문장 활용"), "3개 동시 금지"),
                new CareerAdviceResponse.MentalCare(
                        List.of("남의 시선"), List.of("혼자 산책"), "완벽함은 적의다", "성과 리스트"),
                new CareerAdviceResponse.EnvironmentFit(
                        "규칙과 체계", "대기업", "시니어 상사", "객관적 논의", "창가", "기술 존중"),
                new CareerAdviceResponse.WorkStyle("대기업", "멘토형", "신중한 결정", "유연 대처"),
                new CareerAdviceResponse.RelationshipStrategy(
                        "조력자", "깊이 있는 관계", "go-to person", "데이터 논의", "전문가 네트워크"),
                new CareerAdviceResponse.CareerTimeline(
                        2026,
                        Map.of("March", new CareerAdviceResponse.MonthFortune("적극기", "면접 기회 많음")),
                        List.of(new CareerAdviceResponse.PivotPoint("March", "적극기", 9, "정관 절정")),
                        List.of("May"), "급하게 결정하지 말 것"),
                "己 일간 · 정관 기운 기반 | 2026년 관운 분석 (H1)"
        );
    }

    private CompatibilityResponse buildMockCompatibilityResponse() {
        return new CompatibilityResponse(
                null,
                new CompatibilityResponse.RequestContext(
                        "Samsung",
                        new CompatibilityResponse.TargetRoleInfo(JobCategoryEnum.TECH_BACKEND, "Spring Boot 개발자")
                ),
                78, "상생의 궁합입니다.",
                new CompatibilityResponse.TargetRoleAnalysis(85, "시너지", "경고"),
                new CompatibilityResponse.FiveElements(
                        Map.of("金", 2, "水", 1),
                        Map.of("土", 2, "金", 1),
                        "균형 잡힌 오행 분포"
                ),
                new CompatibilityResponse.AnalysisBreakdown(83, 73, 78),
                new CompatibilityResponse.ActionableStrategy(
                        List.of("체계적 설계", "논리적 사고"),
                        "약점 방어 전략",
                        new CompatibilityResponse.ActionableStrategy.BestTiming(
                                List.of("2026-05-12"), "오전 09:00 ~ 11:00")
                ),
                List.of(new CompatibilityResponse.InterviewQuestion("예상 질문", "의도")),
                List.of(new CompatibilityResponse.RoleCompatibility("백엔드 전문가", 88, "이유", "강력 추천")),
                List.of(new CompatibilityResponse.MonthlyForecast(5, 75, ForecastStatus.LUCKY, "조언")),
                List.of("주의사항 1")
        );
    }
}
