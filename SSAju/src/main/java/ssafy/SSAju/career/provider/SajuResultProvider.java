package ssafy.SSAju.career.provider;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import ssafy.SSAju.annotation.DistributedLock;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.exception.DataAccessException;
import ssafy.SSAju.exception.InvalidSajuDataException;
import ssafy.SSAju.repository.SajuResultRepository;
import ssafy.SSAju.service.SajuResultWriteService;

@Component
@RequiredArgsConstructor
public class SajuResultProvider {

    private final SajuResultRepository sajuResultRepository;
    private final SajuResultWriteService sajuResultWriteService;

    /**
     * userProfile 단위 분산락으로 동시 생성을 막는다(US5, T033).
     *
     * <p>락은 이 메서드가 끝날 때까지 유지된다고 기대하지만, Redisson 락은 {@code leaseTime}이
     * 명시되면 워치독 자동 연장이 꺼져 고정된 임대시간(기본 5000ms)이 지나면 무조건 풀린다.
     * 그 극히 드문 경우까지 배제할 수는 없으므로(UserProfileProvider와 동일한 이유),
     * {@link DataIntegrityViolationException}을 "불가능한 상황"으로 가정하지 않고 재조회해서
     * 동시 저장된 행을 반환한다. {@code saveNewResult}가 별도 트랜잭션(호출자인 findOrCreate엔
     * @Transactional이 없으므로 REQUIRES_NEW 없이도 격리됨)이라 재조회해도 안전하다.
     */
    @DistributedLock(key = "'lock:saju-result:' + #userProfile.id")
    public SajuResult findOrCreate(User user, UserProfile userProfile, SajuResult newResult) {
        if (user == null || userProfile == null || newResult == null) {
            throw new InvalidSajuDataException(ErrorMessageConstants.SAJU_PROFILE_NULL.getMessage());
        }
        if (newResult.getUserProfile() != null
                && !newResult.getUserProfile().equals(userProfile)) {
            throw new InvalidSajuDataException(ErrorMessageConstants.USER_PROFILE_MISMATCH.getMessage());
        }

        return sajuResultRepository.findByUserAndUserProfile(user, userProfile)
                .orElseGet(() -> {
                    try {
                        return sajuResultWriteService.saveNewResult(user, userProfile, newResult);
                    } catch (DataIntegrityViolationException ex) {
                        return sajuResultRepository.findByUserAndUserProfile(user, userProfile)
                                .orElseThrow(() -> new DataAccessException(
                                        ErrorMessageConstants.SAJU_RESULT_ACCESS_FAILED.getMessage(), ex));
                    }
                });
    }
}
