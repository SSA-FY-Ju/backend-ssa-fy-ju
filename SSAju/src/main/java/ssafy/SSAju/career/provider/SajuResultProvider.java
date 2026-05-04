package ssafy.SSAju.career.provider;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.exception.DataAccessException;
import ssafy.SSAju.exception.InvalidSajuDataException;
import ssafy.SSAju.repository.SajuResultRepository;

@Component
@RequiredArgsConstructor
public class SajuResultProvider {

    private final SajuResultRepository sajuResultRepository;

    public SajuResult findOrCreate(UserProfile userProfile, SajuResult newResult) {
        if (userProfile == null || newResult == null) {
            throw new InvalidSajuDataException(ErrorMessageConstants.SAJU_PROFILE_NULL.getMessage());
        }
        // 소유자 불일치 방어: 호출부 실수 조기 감지
        if (newResult.getUserProfile() != null
                && !newResult.getUserProfile().equals(userProfile)) {
            throw new InvalidSajuDataException(ErrorMessageConstants.USER_PROFILE_MISMATCH.getMessage());
        }
        return sajuResultRepository.findByUserProfile(userProfile)
                .orElseGet(() -> {
                    try {
                        return sajuResultRepository.save(newResult);
                    } catch (DataIntegrityViolationException ex) {
                        return sajuResultRepository.findByUserProfile(userProfile)
                                .orElseThrow(() -> new DataAccessException(ErrorMessageConstants.SAJU_RESULT_ACCESS_FAILED.getMessage(), ex));
                    }
                });
    }
}
