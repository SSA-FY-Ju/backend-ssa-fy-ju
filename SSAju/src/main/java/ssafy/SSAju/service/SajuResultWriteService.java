package ssafy.SSAju.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.career.entity.CareerFortune;
import ssafy.SSAju.career.entity.HiddenStemData;
import ssafy.SSAju.career.entity.SajuFullData;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.entity.TenGodData;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.exception.DataAccessException;
import ssafy.SSAju.exception.InvalidSajuDataException;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import ssafy.SSAju.repository.CareerConsultationRepository;
import ssafy.SSAju.repository.CareerFortuneRepository;
import ssafy.SSAju.repository.HiddenStemDataRepository;
import ssafy.SSAju.repository.SajuResultRepository;
import ssafy.SSAju.repository.TenGodDataRepository;

/**
 * SajuResult의 replace(삭제+저장) 작업을 단일 트랜잭션으로 보호.
 *
 * JPQL 직접 삭제로 JPA cascade delete 락 충돌 방지.
 * @Retryable로 첫 생성 시 race condition(DIVE) 1회 재시도 처리.
 *
 * ⚠️ 로그 작성 규칙:
 * - userId만 사용 (birthDate, birthTime 등 개인정보 절대 금지)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SajuResultWriteService {

    private final SajuResultRepository sajuResultRepository;
    private final TenGodDataRepository tenGodDataRepository;
    private final HiddenStemDataRepository hiddenStemDataRepository;
    private final CareerFortuneRepository careerFortuneRepository;
    private final CareerConsultationRepository careerConsultationRepository;

    /**
     * 해당 유저의 기존 SajuResult 삭제 후 새 SajuResult 저장.
     * 첫 생성 시 race condition: 두 스레드가 동시에 findByUserAndUserProfile() → 없음 → save() → DIVE
     * @Retryable: DIVE 발생 시 100ms 대기 후 1회 재시도 (재시도 시 기존 row 발견 후 삭제+재저장)
     */
    @Retryable(
            retryFor = DataIntegrityViolationException.class,
            maxAttempts = 2,
            backoff = @Backoff(delay = 100)
    )
    @Transactional
    public SajuResult replaceForUser(User user, UserProfile userProfile, SajuResult newResult) {
        if (newResult.getUserProfile() != null
                && !newResult.getUserProfile().equals(userProfile)) {
            throw new InvalidSajuDataException(ErrorMessageConstants.USER_PROFILE_MISMATCH.getMessage());
        }
        sajuResultRepository.findByUserAndUserProfile(user, userProfile).ifPresent(existing -> {
            Long existingId = existing.getId();
            careerConsultationRepository.deleteBySajuResultId(existingId);
            tenGodDataRepository.deleteBySajuResultId(existingId);
            hiddenStemDataRepository.deleteBySajuResultId(existingId);
            careerFortuneRepository.deleteBySajuResultId(existingId);
            sajuResultRepository.deleteByUserAndUserProfileJpql(user, userProfile);
        });
        return sajuResultRepository.saveAndFlush(newResult);
    }

    /**
     * insertOrIgnore로 삽입된 새 SajuResult에 자식 엔티티를 붙여 저장.
     *
     * insertOrIgnore(AUTO COMMIT) 성공 후 자식 저장이 단일 트랜잭션으로 보호되어,
     * 자식 저장 실패 시 롤백되어 root만 남는 불일치 상태를 방지.
     */
    @Transactional
    public SajuResult saveNewResultWithChildren(SajuResult detached, SajuResult source) {
        // 트랜잭션 내에서 재조회 → managed 엔티티 확보 (detached 엔티티의 PersistentBag 조작 방지)
        SajuResult saved = sajuResultRepository.findById(detached.getId())
                .orElseThrow(() -> new DataAccessException(ErrorMessageConstants.SAJU_RESULT_ACCESS_FAILED.getMessage()));

        SajuFullData srcFullData = source.getSajuFullData();
        if (srcFullData != null) {
            saved.assignSajuFullData(SajuFullData.builder()
                    .sajuResult(saved)
                    .yearPillar(srcFullData.getYearPillar())
                    .monthPillar(srcFullData.getMonthPillar())
                    .dayPillar(srcFullData.getDayPillar())
                    .hourPillar(srcFullData.getHourPillar())
                    .dayMaster(srcFullData.getDayMaster())
                    .dayMasterElement(srcFullData.getDayMasterElement())
                    .fiveElements(srcFullData.getFiveElements())
                    .solarCorrection(srcFullData.getSolarCorrection())
                    .build());
        }

        List<TenGodData> tenGods = source.getTenGodDataList().stream()
                .map(e -> TenGodData.builder()
                        .sajuResult(saved)
                        .tenGodName(e.getTenGodName())
                        .score(e.getScore())
                        .build())
                .collect(java.util.stream.Collectors.toList());

        List<HiddenStemData> hiddenStems = source.getHiddenStemDataList().stream()
                .map(e -> HiddenStemData.builder()
                        .sajuResult(saved)
                        .earthlyBranch(e.getEarthlyBranch())
                        .hiddenStem(e.getHiddenStem())
                        .build())
                .collect(java.util.stream.Collectors.toList());

        saved.assignTenGodData(tenGods);
        saved.assignHiddenStemData(hiddenStems);

        CareerFortune cf = source.getCareerFortune();
        if (cf != null) {
            saved.assignCareerFortune(CareerFortune.builder()
                    .sajuResult(saved)
                    .favoredPeriod(cf.getFavoredPeriod())
                    .confidenceScore(cf.getConfidenceScore())
                    .reasoning(cf.getReasoning())
                    .build());
        }

        return sajuResultRepository.save(saved);
    }

    /**
     * 재시도 후에도 DIVE 발생 시 실행.
     * 중복 키(동시성 경쟁)인 경우에만 무시. 그 외 실제 제약 위반은 재throw.
     */
    @Recover
    public SajuResult recover(DataIntegrityViolationException ex, User user, UserProfile userProfile, SajuResult newResult) {
        if (isDuplicateKeyViolation(ex)) {
            log.debug("SajuResult 동시 생성 감지: userId={} - 기존 결과 반환", user.getId());
            return sajuResultRepository.findByUserAndUserProfile(user, userProfile)
                    .orElseThrow(() -> new DataAccessException(
                            ErrorMessageConstants.SAJU_RESULT_ACCESS_FAILED.getMessage()));
        }
        throw ex;
    }

    private boolean isDuplicateKeyViolation(DataIntegrityViolationException ex) {
        Throwable rootCause = NestedExceptionUtils.getMostSpecificCause(ex);
        return switch (rootCause) {
            case SQLException sqlEx when sqlEx.getErrorCode() == 1062 -> true;  // MySQL
            case SQLException sqlEx when "23505".equals(sqlEx.getSQLState()) -> true;  // H2/PostgreSQL
            default -> false;
        };
    }
}
