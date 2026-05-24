package ssafy.SSAju.career.provider;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.exception.DataAccessException;
import ssafy.SSAju.exception.InvalidSajuDataException;
import ssafy.SSAju.repository.SajuResultJdbcRepository;
import ssafy.SSAju.repository.SajuResultRepository;
import ssafy.SSAju.service.SajuResultWriteService;

@Component
@RequiredArgsConstructor
public class SajuResultProvider {

    private final SajuResultRepository sajuResultRepository;
    private final SajuResultJdbcRepository sajuResultJdbcRepository;
    private final SajuResultWriteService sajuResultWriteService;

    public SajuResult findOrCreate(User user, UserProfile userProfile, SajuResult newResult) {
        if (user == null || userProfile == null || newResult == null) {
            throw new InvalidSajuDataException(ErrorMessageConstants.SAJU_PROFILE_NULL.getMessage());
        }
        if (newResult.getUserProfile() != null
                && !newResult.getUserProfile().equals(userProfile)) {
            throw new InvalidSajuDataException(ErrorMessageConstants.USER_PROFILE_MISMATCH.getMessage());
        }

        var existing = sajuResultRepository.findByUserAndUserProfile(user, userProfile);
        if (existing.isPresent()) {
            return existing.get();
        }

        int inserted = sajuResultJdbcRepository.insertOrIgnore(newResult);

        SajuResult saved = sajuResultRepository.findByUserAndUserProfile(user, userProfile)
                .orElseThrow(() -> new DataAccessException(ErrorMessageConstants.SAJU_RESULT_ACCESS_FAILED.getMessage()));

        if (inserted == 1) {
            return sajuResultWriteService.saveNewResultWithChildren(saved, newResult);
        }
        return saved;
    }
}
