package ssafy.SSAju.admin.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ssafy.SSAju.admin.config.AdminConfig;
import ssafy.SSAju.admin.dto.DashboardDTO;
import ssafy.SSAju.admin.dto.FeedbackStatDTO;
import ssafy.SSAju.admin.repository.AdminAnalyticsQueryRepository;
import ssafy.SSAju.admin.repository.AdminDailyUsageQueryRepository;
import ssafy.SSAju.admin.repository.AdminFeedbackQueryRepository;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminDashboardService 단위 테스트")
class AdminDashboardServiceTest {

    @Mock
    private AdminAnalyticsQueryRepository analyticsQueryRepository;

    @Mock
    private AdminDailyUsageQueryRepository dailyUsageQueryRepository;

    @Mock
    private AdminFeedbackQueryRepository feedbackQueryRepository;

    @Mock
    private AdminConfig adminConfig;

    @InjectMocks
    private AdminDashboardService adminDashboardService;

    private AdminConfig.Api stubApi(int dailyLimit) {
        AdminConfig.Api api = new AdminConfig.Api();
        api.setDailyLimit(dailyLimit);
        return api;
    }

    @Test
    @DisplayName("정상 데이터 → DashboardDTO 올바르게 집계")
    void getDashboardData_normalData_aggregatesCorrectly() {
        given(adminConfig.getApi()).willReturn(stubApi(3));
        given(analyticsQueryRepository.findDailyAnalysisSummary(any(LocalDate.class)))
                .willReturn(Map.of("SAJU", 5L, "CAREER_CONSULTATION", 3L, "COMPANY_COMPATIBILITY", 2L));
        given(dailyUsageQueryRepository.countDailyLimitExhausted(any(LocalDate.class), anyInt()))
                .willReturn(4L);
        given(feedbackQueryRepository.findFeedbackStats())
                .willReturn(new FeedbackStatDTO(
                        Map.of("CAREER_CONSULTATION", 10L),
                        Map.of("CAREER_CONSULTATION", 3L),
                        13L
                ));

        DashboardDTO result = adminDashboardService.getDashboardData();

        assertThat(result.totalAnalysis()).isEqualTo(10L);
        assertThat(result.analysisTypeBreakdown()).containsEntry("SAJU", 5L);
        assertThat(result.dailyLimitExhaustedCount()).isEqualTo(4L);
        assertThat(result.feedbackSummary().satisfiedCount()).isEqualTo(10L);
        assertThat(result.feedbackSummary().unsatisfiedCount()).isEqualTo(3L);
        assertThat(result.feedbackSummary().totalCount()).isEqualTo(13L);
    }

    @Test
    @DisplayName("분석 데이터 없음 → totalAnalysis = 0")
    void getDashboardData_noAnalysis_returnZeroTotal() {
        given(adminConfig.getApi()).willReturn(stubApi(3));
        given(analyticsQueryRepository.findDailyAnalysisSummary(any(LocalDate.class)))
                .willReturn(Map.of("SAJU", 0L, "CAREER_CONSULTATION", 0L, "COMPANY_COMPATIBILITY", 0L));
        given(dailyUsageQueryRepository.countDailyLimitExhausted(any(LocalDate.class), anyInt()))
                .willReturn(0L);
        given(feedbackQueryRepository.findFeedbackStats())
                .willReturn(new FeedbackStatDTO(Map.of(), Map.of(), 0L));

        DashboardDTO result = adminDashboardService.getDashboardData();

        assertThat(result.totalAnalysis()).isZero();
        assertThat(result.dailyLimitExhaustedCount()).isZero();
        assertThat(result.feedbackSummary().satisfiedCount()).isZero();
        assertThat(result.feedbackSummary().unsatisfiedCount()).isZero();
        assertThat(result.feedbackSummary().totalCount()).isZero();
    }

    @Test
    @DisplayName("여러 FeedbackType에 만족/불만족 분산 → 합산 정확")
    void getDashboardData_multipleFeedbackTypes_sumsCorrectly() {
        given(adminConfig.getApi()).willReturn(stubApi(3));
        given(analyticsQueryRepository.findDailyAnalysisSummary(any(LocalDate.class)))
                .willReturn(Map.of("SAJU", 1L, "CAREER_CONSULTATION", 1L, "COMPANY_COMPATIBILITY", 1L));
        given(dailyUsageQueryRepository.countDailyLimitExhausted(any(LocalDate.class), anyInt()))
                .willReturn(0L);
        given(feedbackQueryRepository.findFeedbackStats())
                .willReturn(new FeedbackStatDTO(
                        Map.of("CAREER_CONSULTATION", 7L, "COMPANY_COMPATIBILITY", 3L),
                        Map.of("CAREER_CONSULTATION", 2L, "COMPANY_COMPATIBILITY", 1L),
                        13L
                ));

        DashboardDTO result = adminDashboardService.getDashboardData();

        assertThat(result.feedbackSummary().satisfiedCount()).isEqualTo(10L);
        assertThat(result.feedbackSummary().unsatisfiedCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("일일 제한 소진 카운트 → AdminConfig dailyLimit 값 사용")
    void getDashboardData_usesConfiguredDailyLimit() {
        given(adminConfig.getApi()).willReturn(stubApi(5));
        given(analyticsQueryRepository.findDailyAnalysisSummary(any(LocalDate.class)))
                .willReturn(Map.of());
        given(dailyUsageQueryRepository.countDailyLimitExhausted(any(LocalDate.class), anyInt()))
                .willReturn(2L);
        given(feedbackQueryRepository.findFeedbackStats())
                .willReturn(new FeedbackStatDTO(Map.of(), Map.of(), 0L));

        DashboardDTO result = adminDashboardService.getDashboardData();

        assertThat(result.dailyLimitExhaustedCount()).isEqualTo(2L);
    }
}
