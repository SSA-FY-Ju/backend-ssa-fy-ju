package ssafy.SSAju.career.provider;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.exception.DataAccessException;
import ssafy.SSAju.repository.SajuResultRepository;

@Component
@RequiredArgsConstructor
public class SajuResultProvider {

    private final SajuResultRepository sajuResultRepository;

    public SajuResult findOrCreate(UserProfile userProfile, SajuResult newResult) {
        return sajuResultRepository.findByUserProfile(userProfile)
                .orElseGet(() -> {
                    try {
                        return sajuResultRepository.save(newResult);
                    } catch (DataIntegrityViolationException ex) {
                        return sajuResultRepository.findByUserProfile(userProfile)
                                .orElseThrow(() -> new DataAccessException("SajuResult 조회/생성 실패", ex));
                    }
                });
    }
}
