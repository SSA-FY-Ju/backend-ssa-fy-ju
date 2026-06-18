package ssafy.SSAju.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.admin.config.AdminConfig;
import ssafy.SSAju.admin.dto.DashboardDTO;
import ssafy.SSAju.admin.dto.FeedbackStatDTO;
import ssafy.SSAju.admin.repository.AdminAnalyticsQueryRepository;
import ssafy.SSAju.admin.repository.AdminDailyUsageQueryRepository;
import ssafy.SSAju.admin.repository.AdminFeedbackQueryRepository;

import java.time.LocalDate;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService extends AdminBaseService {

    private final AdminAnalyticsQueryRepository analyticsQueryRepository;
    private final AdminDailyUsageQueryRepository dailyUsageQueryRepository;
    private final AdminFeedbackQueryRepository feedbackQueryRepository;
    private final AdminConfig adminConfig;

    public DashboardDTO getDashboardData() {
        LocalDate today = todaySeoul();

        Map<String, Long> breakdown = analyticsQueryRepository.findDailyAnalysisSummary(today);
        long totalAnalysis = breakdown.values().stream().mapToLong(Long::longValue).sum();

        long exhaustedCount = dailyUsageQueryRepository.countDailyLimitExhausted(
                today, adminConfig.getApi().getDailyLimit());

        DashboardDTO.FeedbackSummary feedbackSummary = buildFeedbackSummary();

        log.debug("대시보드 데이터 조회: date={}, total={}, exhausted={}", today, totalAnalysis, exhaustedCount);
        return new DashboardDTO(totalAnalysis, breakdown, exhaustedCount, feedbackSummary);
    }

    private DashboardDTO.FeedbackSummary buildFeedbackSummary() {
        FeedbackStatDTO stat = feedbackQueryRepository.findFeedbackStats();
        long satisfied = stat.satisfiedCountByFeedbackType().values().stream()
                .mapToLong(Long::longValue).sum();
        long unsatisfied = stat.unsatisfiedCountByFeedbackType().values().stream()
                .mapToLong(Long::longValue).sum();
        return new DashboardDTO.FeedbackSummary(satisfied, unsatisfied, stat.totalFeedbackCount());
    }
}
