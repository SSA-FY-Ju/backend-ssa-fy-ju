package ssafy.SSAju.dto.response;

import ssafy.SSAju.career.entity.UserProfile;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 기본 출생정보 응답. 기본값이 설정되지 않은 사용자는 이 객체 대신 {@code data = null}을 받는다.
 */
public record DefaultProfileResponse(
        LocalDate birthDate,
        LocalTime birthTime
) {
    public static DefaultProfileResponse from(UserProfile userProfile) {
        return new DefaultProfileResponse(userProfile.getBirthDate(), userProfile.getBirthTime());
    }
}
