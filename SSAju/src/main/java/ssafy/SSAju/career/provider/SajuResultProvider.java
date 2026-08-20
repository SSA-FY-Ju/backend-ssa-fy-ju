package ssafy.SSAju.career.provider;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ssafy.SSAju.annotation.DistributedLock;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.entity.User;
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
     * 락 안에서는 조회 후 없으면 생성하는 단순한 흐름만 남는다 — insertOrIgnore로
     * 경합을 흡수하던 이전 방식은 더 이상 필요하지 않다.
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
                .orElseGet(() -> sajuResultWriteService.saveNewResult(user, userProfile, newResult));
    }
}
