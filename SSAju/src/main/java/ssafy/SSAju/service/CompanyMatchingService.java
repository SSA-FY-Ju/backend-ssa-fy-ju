package ssafy.SSAju.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ssafy.SSAju.career.entity.CompanyCompatibility;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.provider.UserProfileProvider;
import ssafy.SSAju.dto.request.CompatibilityRequest;
import ssafy.SSAju.dto.response.CompatibilityResponse;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.exception.UserNotFoundException;
import ssafy.SSAju.repository.CompanyCompatibilityRepository;
import ssafy.SSAju.repository.UserRepository;

import java.time.Clock;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.Optional;

/**
 * 기업/직무 궁합 분석 오케스트레이션 서비스.
 *
 * <p><strong>이 클래스의 단일 책임</strong>: 사용자/프로필 확인 후 락 없이 빠르게 처리 가능한
 * 1차 캐시 조회(완료된 이번 달 분석 재사용)만 담당한다. 캐시 미스는
 * {@link CompanyCompatibilityLockedAnalysisService}로 위임한다.
 *
 * <p><strong>락+쿼터 로직을 별도 클래스로 분리한 이유(US5, T035)</strong>: 동일 클래스 내
 * self-invocation에서는 {@code @DistributedLock}을 처리하는 Spring AOP 프록시가 작동하지
 * 않는다. 락 없는 빠른 경로(1차 캐시 조회)와 락으로 보호해야 하는 경로(쿼터 차감 + 사주
 * 계산/AI 호출/저장)를 같은 클래스에 두면 후자를 호출할 때 프록시를 우회하게 되므로,
 * {@code ConsultationSaveService}/{@code ConsultationInsertService}와 동일한 이유로
 * 별도 빈으로 분리했다.
 *
 * <p>{@code @Transactional} 없음: FastAPI 외부 I/O 동안 DB 커넥션을 점유하지 않도록
 * 트랜잭션을 분리.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyMatchingService {

    private static final LocalTime DEFAULT_BIRTH_TIME = LocalTime.of(12, 0);

    private final UserProfileProvider userProfileProvider;
    private final CompanyCompatibilityRepository companyCompatibilityRepository;
    private final CompatibilityChildReadService childReadService;
    private final UserRepository userRepository;
    /** KST 기준 현재 월 계산용 Clock. 테스트에서 고정 시각 주입 가능. */
    private final Clock clock;
    private final CompanyCompatibilityLockedAnalysisService lockedAnalysisService;

    public CompatibilityResponse analyzeCompatibility(CompatibilityRequest request, Long userId) {
        log.info("기업 궁합 분석 시작");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        LocalTime userBirthTime = resolveUserBirthTime(request);
        UserProfile userProfile = userProfileProvider.findOrCreate(
                request.userBirthDate(), userBirthTime);

        YearMonth now = YearMonth.now(clock);
        Integer compatibilityMonth = now.getYear() * 100 + now.getMonthValue();

        // ─── 락 없는 1차 캐시 조회 (대부분의 반복 요청은 여기서 끝난다) ──────────────
        Optional<CompanyCompatibility> cachedOpt = companyCompatibilityRepository
                .findByUser_IdAndUserProfile_IdAndCompanyNameAndTargetRoleCategoryAndCompatibilityMonth(
                        userId, userProfile.getId(),
                        request.companyName(), request.targetRole().category(), compatibilityMonth)
                .filter(CompanyCompatibility::isCompleted);

        if (cachedOpt.isPresent()) {
            log.info("이번 달 궁합 분석 캐시 히트 (compatibilityId={})", cachedOpt.get().getId());
            return childReadService.buildFromExisting(cachedOpt.get(), request);
        }

        // ─── 캐시 미스: 락 안에서 더블체크 후 쿼터 차감 + 사주 계산/AI 호출/저장 ──────
        return lockedAnalysisService.analyzeWithLock(
                request, userId, user, userProfile, compatibilityMonth, userBirthTime);
    }

    private LocalTime resolveUserBirthTime(CompatibilityRequest request) {
        return request.userBirthTime() != null ? request.userBirthTime() : DEFAULT_BIRTH_TIME;
    }
}
