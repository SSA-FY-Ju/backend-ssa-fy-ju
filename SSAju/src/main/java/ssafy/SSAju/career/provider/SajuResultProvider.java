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
        if (userProfile == null || newResult == null) {
            throw new IllegalArgumentException("userProfile과 newResult는 null이 아니어야 합니다");
        }
        // 소유자 불일치 방어: 호출부 실수 조기 감지
        if (newResult.getUserProfile() != null
                && !newResult.getUserProfile().equals(userProfile)) {
            throw new IllegalArgumentException(
                "newResult의 userProfile이 전달받은 userProfile과 불일치합니다");
        }
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
