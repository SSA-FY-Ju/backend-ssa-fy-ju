package ssafy.SSAju.career.provider;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
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
                                .orElseThrow(() -> new DataAccessException(ErrorMessageConstants.USER_PROFILE_ACCESS_FAILED.getMessage(), ex));
                    }
                });
    }
}
