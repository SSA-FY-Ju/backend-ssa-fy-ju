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
     *
     * <p>락은 이 메서드가 끝날 때까지 유지된다고 기대하지만, Redisson 락은 {@code leaseTime}이
     * 명시되면 워치독 자동 연장이 꺼져 고정된 임대시간(기본 5000ms)이 지나면 무조건 풀린다.
     * DB 커넥션 풀 고갈 등으로 이 메서드 실행이 그보다 오래 걸리면, 락이 중간에 풀려 두 번째
     * 요청이 같은 락을 잡고 들어올 수 있다 — 그래서 {@link DataIntegrityViolationException}을
     * "불가능한 상황"으로 가정하지 않고, 그 경우 재조회해서 동시 삽입된 행을 반환한다.
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
                        return userProfileRepository
                                .findByBirthDateAndBirthTime(birthDate, birthTime)
                                .orElseThrow(() -> new DataAccessException(
                                        ErrorMessageConstants.USER_PROFILE_ACCESS_FAILED.getMessage(), ex));
                    }
                });
    }
}
