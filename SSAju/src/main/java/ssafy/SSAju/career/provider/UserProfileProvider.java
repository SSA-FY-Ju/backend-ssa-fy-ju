package ssafy.SSAju.career.provider;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import ssafy.SSAju.annotation.DistributedLock;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.exception.DataAccessException;
import ssafy.SSAju.repository.UserProfileRepository;

import java.time.LocalDate;
import java.time.LocalTime;

@Component
@RequiredArgsConstructor
public class UserProfileProvider {

    private final UserProfileRepository userProfileRepository;

    /**
     * (birthDate, birthTime) 단위 분산락으로 동시 생성을 막는다(US5, T034).
     * 락이 경합 자체를 막으므로 저장 시 {@link DataIntegrityViolationException}이 발생한다면
     * 락 밖 경로(예: 데이터 이관)에서 비롯된 진짜 무결성 위반으로 간주해 그대로 전파한다.
     */
    @DistributedLock(key = "'lock:user-profile:' + #birthDate + ':' + #birthTime")
    public UserProfile findOrCreate(LocalDate birthDate, LocalTime birthTime) {
        return userProfileRepository
                .findByBirthDateAndBirthTime(birthDate, birthTime)
                .orElseGet(() -> {
                    try {
                        return userProfileRepository.save(
                                UserProfile.builder()
                                        .birthDate(birthDate)
                                        .birthTime(birthTime)
                                        .build());
                    } catch (DataIntegrityViolationException ex) {
                        throw new DataAccessException(ErrorMessageConstants.USER_PROFILE_ACCESS_FAILED.getMessage(), ex);
                    }
                });
    }
}
