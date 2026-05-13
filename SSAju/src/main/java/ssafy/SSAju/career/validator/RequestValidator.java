package ssafy.SSAju.career.validator;

import org.springframework.stereotype.Component;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.exception.InvalidSajuDataException;

import java.time.LocalDate;
import java.time.LocalTime;

@Component
public class RequestValidator {

    private static final LocalDate MIN_SUPPORTED_DATE = LocalDate.of(1900, 1, 1);

    public void validateBirthInfo(LocalDate birthDate, LocalTime birthTime) {
        if (birthDate == null) {
            throw new InvalidSajuDataException(ErrorMessageConstants.BIRTH_DATE_REQUIRED.getMessage());
        }
        if (birthDate.isBefore(MIN_SUPPORTED_DATE)) {
            throw new InvalidSajuDataException(ErrorMessageConstants.BIRTH_DATE_TOO_OLD.getMessage());
        }
        if (birthTime == null) {
            throw new InvalidSajuDataException(ErrorMessageConstants.BIRTH_TIME_REQUIRED.getMessage());
        }
    }
}
