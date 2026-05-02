package ssafy.SSAju.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.entity.UserProfile;
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

    /**
     * 기존 SajuResult 삭제 후 새 SajuResult 저장.
     * 첫 생성 시 race condition: 두 스레드가 동시에 findByUserProfile() → 없음 → save() → DIVE
     * @Retryable: DIVE 발생 시 100ms 대기 후 1회 재시도 (재시도 시 기존 row 발견 후 삭제+재저장)
     */
    @Retryable(
            retryFor = DataIntegrityViolationException.class,
            maxAttempts = 2,
            backoff = @Backoff(delay = 100)
    )
    @Transactional
    public void replaceForUserProfile(UserProfile userProfile, SajuResult newResult) {
        sajuResultRepository.findByUserProfile(userProfile).ifPresent(existing -> {
            Long existingId = existing.getId();
            tenGodDataRepository.deleteBySajuResultId(existingId);
            hiddenStemDataRepository.deleteBySajuResultId(existingId);
            careerFortuneRepository.deleteBySajuResultId(existingId);
            sajuResultRepository.deleteByUserProfileJpql(userProfile);
        });
        sajuResultRepository.save(newResult);
    }

    /**
     * 재시도 후에도 DIVE 발생 시 실행.
     * 다른 스레드가 최종 commit에 성공한 상태이므로, 기존 데이터를 그대로 사용.
     * 예외를 throw하지 않아 caller는 정상 완료로 처리됨.
     */
    @Recover
    public void recover(DataIntegrityViolationException ex, UserProfile userProfile, SajuResult newResult) {
        log.debug("SajuResult 동시 생성 감지: userProfileId={} - 기존 결과 유지", userProfile.getId());
    }
}
