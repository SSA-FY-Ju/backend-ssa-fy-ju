package ssafy.SSAju.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import ssafy.SSAju.career.caller.ConsultationOpenAICaller;
import ssafy.SSAju.dto.external.FastAPIResponse;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.entity.enums.UserRole;
import ssafy.SSAju.entity.enums.UserStatus;
import ssafy.SSAju.repository.SajuResultRepository;
import ssafy.SSAju.repository.UserProfileRepository;
import ssafy.SSAju.repository.UserRepository;
import ssafy.SSAju.repository.UserSatisfactionFeedbackRepository;
import ssafy.SSAju.service.DailyApiUsageService;
import ssafy.SSAju.service.SajuDataService;
import ssafy.SSAju.util.JwtUtil;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 진정한 통합 테스트: 외부 API(FastAPI, OpenAI) 경계만 mock하고
 * 서비스·리포지토리는 실제 빈으로 두어 DB 저장·연쇄 호출을 검증합니다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DisplayName("Career API 통합 테스트 (T090) - 영속화·서비스 연쇄 검증")
class CareerApiIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    // ─── 외부 API 경계만 mock ───────────────────────────────────────
    @MockitoBean
    private SajuDataService sajuDataService;       // FastAPI 호출 차단

    @MockitoBean
    private ConsultationOpenAICaller openAICaller; // OpenAI 호출 차단

    @MockitoBean
    private DailyApiUsageService dailyApiUsageService; // H2 호환 SQL 문제 방지

    // ─── 실제 빈: 서비스·리포지토리 (H2 DB) ─────────────────────────
    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private SajuResultRepository sajuResultRepository;

    @Autowired
    private UserSatisfactionFeedbackRepository feedbackRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private String authHeader;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        // 테스트 격리: 각 테스트 전 DB 초기화
        feedbackRepository.deleteAll();
        sajuResultRepository.deleteAll();
        userProfileRepository.deleteAll();
        userRepository.deleteAll();

        // 실제 JWT 인증을 위한 테스트 사용자 생성
        User testUser = userRepository.save(User.builder()
                .email("integration-test@test.com")
                .passwordHash("hash")
                .name("통합테스트")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .termsAgreedAt(LocalDateTime.now())
                .privacyAgreedAt(LocalDateTime.now())
                .build());
        String token = jwtUtil.generateAccessToken(testUser.getId(), testUser.getEmail());
        authHeader = "Bearer " + token;

        // FastAPI stub: 실제 사주 데이터 형태로 구성
        given(sajuDataService.fetchSajuFromFastAPI(any(), any()))
                .willReturn(new FastAPIResponse(
                        List.of("庚", "丙", "己", "辛"),
                        List.of("午", "戌", "未", "寅"),
                        Map.of("木", 1, "火", 2, "土", 1, "金", 2, "水", 2),
                        "庚午", "丙戌", "己未", "辛寅",
                        "14:30", "1990-10-10", null
                ));
    }

    // ─────────────────────────────────────────────────────────────────
    // 관운 분석 영속화 검증
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("T090-1: 관운 분석 요청 → UserProfile·SajuResult가 DB에 저장됨")
    void careerTiming_persistsUserProfileAndSajuResult() throws Exception {
        // When
        mockMvc.perform(post("/api/career/timing")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"birthDate": "1990-10-10", "birthTime": "14:30"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.favoredPeriod").isNotEmpty())
                .andExpect(jsonPath("$.data.confidenceScore").isNumber());

        // Then: 실제 DB 저장 검증
        assertThat(userProfileRepository.count()).isEqualTo(1);
        assertThat(sajuResultRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("T090-2: 동일 생년월일시 중복 요청 → DB에 1건만 유지 (멱등성 보장)")
    void careerTiming_duplicateRequest_idempotent() throws Exception {
        String body = """
                {"birthDate": "1990-10-10", "birthTime": "14:30"}
                """;

        // When: 동일 입력으로 2회 요청
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/career/timing")
                            .header("Authorization", authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());
        }

        // Then: Race Condition 방지 - SajuResult는 1건만 유지
        assertThat(userProfileRepository.count()).isEqualTo(1);
        assertThat(sajuResultRepository.count()).isEqualTo(1);
    }

    // ─────────────────────────────────────────────────────────────────
    // 피드백 저장 영속화 검증 (SajuResult → Feedback 연쇄)
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("T090-3: 관운 분석 후 피드백 저장 → SajuResult 연결 검증")
    void saveFeedback_afterCareerTiming_persistsFeedbackLinkedToSajuResult() throws Exception {
        // Given: 먼저 관운 분석으로 SajuResult 생성
        mockMvc.perform(post("/api/career/timing")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"birthDate": "1990-10-10", "birthTime": "14:30"}
                                """))
                .andExpect(status().isOk());

        Long savedSajuResultId = sajuResultRepository.findAll().get(0).getId();

        // When: 피드백 저장
        mockMvc.perform(post("/api/feedback/satisfaction")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sajuResultId": %d,
                                  "feedbackType": "CAREER_TIMING",
                                  "satisfactionStatus": "SATISFIED",
                                  "feedbackContent": "매우 유익했습니다"
                                }
                                """.formatted(savedSajuResultId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.feedbackId").isNumber())
                .andExpect(jsonPath("$.data.feedbackContent").value("매우 유익했습니다"));

        // Then: 피드백이 DB에 저장됐는지 검증
        assertThat(feedbackRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("T090-4: 존재하지 않는 SajuResultId로 피드백 저장 → 404 Not Found")
    void saveFeedback_withNonExistentSajuResultId_returns404() throws Exception {
        mockMvc.perform(post("/api/feedback/satisfaction")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sajuResultId": 999999,
                                  "feedbackType": "CAREER_TIMING",
                                  "satisfactionStatus": "SATISFIED"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));

        // Then: DB에 아무것도 저장되지 않음
        assertThat(feedbackRepository.count()).isZero();
    }
}
