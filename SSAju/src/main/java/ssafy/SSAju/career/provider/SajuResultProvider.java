package ssafy.SSAju.career.provider;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import ssafy.SSAju.annotation.DistributedLock;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.entity.UserSajuAccess;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.exception.DataAccessException;
import ssafy.SSAju.exception.InvalidSajuDataException;
import ssafy.SSAju.repository.SajuResultRepository;
import ssafy.SSAju.repository.UserSajuAccessRepository;
import ssafy.SSAju.service.SajuResultWriteService;

@Component
@RequiredArgsConstructor
public class SajuResultProvider {

    private final SajuResultRepository sajuResultRepository;
    private final SajuResultWriteService sajuResultWriteService;
    private final UserSajuAccessRepository userSajuAccessRepository;

    /**
     * userProfile 단위 분산락으로 동시 생성을 막는다(US5, T033).
     *
     * <p>락은 이 메서드가 끝날 때까지 유지된다고 기대하지만, Redisson 락은 {@code leaseTime}이
     * 명시되면 워치독 자동 연장이 꺼져 고정된 임대시간(기본 5000ms)이 지나면 무조건 풀린다.
     * 그 극히 드문 경우까지 배제할 수는 없으므로(UserProfileProvider와 동일한 이유),
     * {@link DataIntegrityViolationException}을 "불가능한 상황"으로 가정하지 않고 재조회해서
     * 동시 저장된 행을 반환한다. {@code saveNewResult}가 별도 트랜잭션(호출자인 findOrCreate엔
     * @Transactional이 없으므로 REQUIRES_NEW 없이도 격리됨)이라 재조회해도 안전하다.
     *
     * <p>SajuResult는 userProfile 기준 정본이라 여러 사용자가 공유할 수 있다(B1). 정본을 찾거나
     * 새로 만든 뒤에는 호출자(user)가 이 정본에 접근한 이력이 없으면 {@link UserSajuAccess}
     * 매핑을 생성해 마이페이지/피드백 등에서 소유권 확인이 가능하도록 한다.
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

        SajuResult result = sajuResultRepository.findByUserProfile(userProfile)
                .orElseGet(() -> {
                    try {
                        return sajuResultWriteService.saveNewResult(userProfile, newResult);
                    } catch (DataIntegrityViolationException ex) {
                        return sajuResultRepository.findByUserProfile(userProfile)
                                .orElseThrow(() -> new DataAccessException(
                                        ErrorMessageConstants.SAJU_RESULT_ACCESS_FAILED.getMessage(), ex));
                    }
                });

        ensureAccess(user, result);
        return result;
    }

    /**
     * userProfile 단위 분산락 안에서 호출되므로(위 findOrCreate) 동일 사용자의 중복 삽입 경합은
     * 사실상 발생하지 않는다. 그럼에도 유니크 제약 위반은 "이미 매핑이 존재한다"는 뜻일 수 있으므로,
     * 예외를 무시하기 전에 반드시 재조회로 실제로 매핑이 존재하는지 확인한다 — 그렇지 않다면
     * (예: user/sajuResult FK 위반 등 무관한 원인) 원본 예외를 그대로 전파한다.
     */
    private void ensureAccess(User user, SajuResult sajuResult) {
        if (userSajuAccessRepository.existsByUserIdAndSajuResultId(user.getId(), sajuResult.getId())) {
            return;
        }
        try {
            userSajuAccessRepository.save(UserSajuAccess.builder()
                    .user(user)
                    .sajuResult(sajuResult)
                    .build());
        } catch (DataIntegrityViolationException ex) {
            if (!userSajuAccessRepository.existsByUserIdAndSajuResultId(user.getId(), sajuResult.getId())) {
                throw ex;
            }
            // 이미 다른 요청이 매핑을 생성함 — 무시
        }
    }
}
