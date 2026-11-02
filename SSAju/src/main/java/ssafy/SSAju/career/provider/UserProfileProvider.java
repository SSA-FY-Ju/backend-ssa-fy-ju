package ssafy.SSAju.career.provider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.exception.DataAccessException;
import ssafy.SSAju.repository.UserProfileRepository;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 출생정보(생년월일·시각)에 해당하는 공유 프로필을 조회하거나 최초 1회 생성한다.
 *
 * <p>분산락을 사용하지 않는다 — (birthDate, birthTime) 유일 제약이 중복 생성을 이미 막는다.
 * 동시에 같은 출생정보가 들어오면 한쪽은 제약 위반으로 실패하므로, 그 경우 재조회해서 먼저 저장된
 * 행을 반환한다. 저장 호출은 Spring Data JPA의 독립 트랜잭션이라 실패 후 재조회가 안전하다.
 *
 * <p>프로필의 출생정보는 생성 후 변경하지 않는다 — 여러 사용자와 과거 분석이 이 행을 공유한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserProfileProvider {

    private final UserProfileRepository userProfileRepository;

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
                        log.info("출생정보 프로필 생성 경합 — 저장된 정본 재조회");
                        return userProfileRepository
                                .findByBirthDateAndBirthTime(birthDate, birthTime)
                                .orElseThrow(() -> new DataAccessException(
                                        ErrorMessageConstants.USER_PROFILE_ACCESS_FAILED.getMessage(), ex));
                    }
                });
    }
}
