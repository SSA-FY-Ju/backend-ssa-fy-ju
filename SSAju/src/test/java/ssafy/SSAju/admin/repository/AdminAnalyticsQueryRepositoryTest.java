package ssafy.SSAju.admin.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ssafy.SSAju.admin.dto.AnalyticsListDTO;
import ssafy.SSAju.career.entity.CareerConsultation;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.entity.UserSajuAccess;
import ssafy.SSAju.dto.external.CareerAdviceResponse;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.entity.enums.UserRole;
import ssafy.SSAju.entity.enums.UserStatus;
import ssafy.SSAju.repository.CareerConsultationRepository;
import ssafy.SSAju.repository.SajuResultRepository;
import ssafy.SSAju.repository.UserProfileRepository;
import ssafy.SSAju.repository.UserRepository;
import ssafy.SSAju.repository.UserSajuAccessRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B1: SajuResult/CareerConsultation은 여러 사용자가 공유하는 정본이라, "정본 수"와
 * "정본에 접근하는 사용자 수"가 다를 수 있다. 관리자 대시보드 요약(findDailyAnalysisSummary)이
 * 목록(findAnalyticsByDateAndType)과 동일하게 "접근자 수" 기준으로 집계하는지 검증한다.
 */
@SpringBootTest
@DisplayName("AdminAnalyticsQueryRepository — 정본 공유 시 요약/목록 카운트 일치 검증")
class AdminAnalyticsQueryRepositoryTest {

    @Autowired private AdminAnalyticsQueryRepository analyticsQueryRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private SajuResultRepository sajuResultRepository;
    @Autowired private UserSajuAccessRepository userSajuAccessRepository;
    @Autowired private CareerConsultationRepository careerConsultationRepository;

    @BeforeEach
    void setUp() {
        careerConsultationRepository.deleteAllInBatch();
        userSajuAccessRepository.deleteAllInBatch();
        sajuResultRepository.deleteAllInBatch();
        userProfileRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    private User newUser(String email) {
        return userRepository.save(User.builder()
                .email(email)
                .passwordHash("hash")
                .name("테스트")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .termsAgreedAt(Instant.now())
                .privacyAgreedAt(Instant.now())
                .build());
    }

    private static CareerAdviceResponse minimalAdvice() {
        return new CareerAdviceResponse(
                List.of(), List.of(), List.of(), List.of(),
                null, null, null, null, null, null, null, null, null,
                List.of("정관"), "일간 설명", "오행 분석");
    }

    @Test
    @DisplayName("SAJU: 정본 1건을 2명이 공유하면 요약 카운트는 목록 행 수(2)와 일치한다")
    void countSaju_sharedResult_matchesListRowCount() {
        User userA = newUser("share-a@test.com");
        User userB = newUser("share-b@test.com");
        UserProfile profile = userProfileRepository.save(UserProfile.builder()
                .birthDate(LocalDate.of(1990, 7, 15))
                .birthTime(LocalTime.of(12, 0))
                .build());
        SajuResult sajuResult = sajuResultRepository.save(SajuResult.builder()
                .userProfile(profile)
                .build());
        userSajuAccessRepository.save(UserSajuAccess.builder().user(userA).sajuResult(sajuResult).build());
        userSajuAccessRepository.save(UserSajuAccess.builder().user(userB).sajuResult(sajuResult).build());

        LocalDate today = LocalDate.now(ssafy.SSAju.admin.service.AdminBaseService.SEOUL_ZONE);
        long summaryCount = analyticsQueryRepository.findDailyAnalysisSummary(today).get("SAJU");
        List<AnalyticsListDTO> listRows = analyticsQueryRepository.findAnalyticsByDateAndType(
                "SAJU", today, today, 0, 20);

        assertThat(summaryCount).isEqualTo(2);
        assertThat(listRows).hasSize(2);
        assertThat(summaryCount).isEqualTo(listRows.size());
    }

    @Test
    @DisplayName("CAREER_CONSULTATION: 정본 1건을 2명이 공유하면 요약 카운트는 목록 행 수(2)와 일치한다")
    void countConsultation_sharedResult_matchesListRowCount() {
        User userA = newUser("consult-a@test.com");
        User userB = newUser("consult-b@test.com");
        UserProfile profile = userProfileRepository.save(UserProfile.builder()
                .birthDate(LocalDate.of(1992, 3, 3))
                .birthTime(LocalTime.of(9, 30))
                .build());
        SajuResult sajuResult = sajuResultRepository.save(SajuResult.builder()
                .userProfile(profile)
                .build());
        userSajuAccessRepository.save(UserSajuAccess.builder().user(userA).sajuResult(sajuResult).build());
        userSajuAccessRepository.save(UserSajuAccess.builder().user(userB).sajuResult(sajuResult).build());
        careerConsultationRepository.save(CareerConsultation.builder()
                .sajuResult(sajuResult)
                .openaiModelVersion("gpt-4o-mini")
                .consultationMonth(YearMonth.now().getYear() * 100 + YearMonth.now().getMonthValue())
                .resultJson(minimalAdvice())
                .build());

        LocalDate today = LocalDate.now(ssafy.SSAju.admin.service.AdminBaseService.SEOUL_ZONE);
        long summaryCount = analyticsQueryRepository.findDailyAnalysisSummary(today).get("CAREER_CONSULTATION");
        List<AnalyticsListDTO> listRows = analyticsQueryRepository.findAnalyticsByDateAndType(
                "CAREER_CONSULTATION", today, today, 0, 20);

        assertThat(summaryCount).isEqualTo(2);
        assertThat(listRows).hasSize(2);
        assertThat(summaryCount).isEqualTo(listRows.size());
    }

    @Test
    @DisplayName("SAJU: 접근 매핑이 없는 정본은 요약/목록 어느 쪽에도 잡히지 않는다")
    void countSaju_resultWithoutAccessMapping_excludedFromBoth() {
        UserProfile profile = userProfileRepository.save(UserProfile.builder()
                .birthDate(LocalDate.of(1985, 11, 20))
                .birthTime(LocalTime.of(18, 0))
                .build());
        sajuResultRepository.save(SajuResult.builder().userProfile(profile).build());
        // 의도적으로 UserSajuAccess를 생성하지 않음 (접근 불가능한 정본)

        LocalDate today = LocalDate.now(ssafy.SSAju.admin.service.AdminBaseService.SEOUL_ZONE);
        long summaryCount = analyticsQueryRepository.findDailyAnalysisSummary(today).get("SAJU");
        List<AnalyticsListDTO> listRows = analyticsQueryRepository.findAnalyticsByDateAndType(
                "SAJU", today, today, 0, 20);

        assertThat(summaryCount).isZero();
        assertThat(listRows).isEmpty();
    }
}
