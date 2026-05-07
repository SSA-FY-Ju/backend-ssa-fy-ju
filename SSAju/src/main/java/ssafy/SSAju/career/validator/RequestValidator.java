package ssafy.SSAju.career.validator;

import org.springframework.stereotype.Component;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.exception.InvalidSajuDataException;

import java.time.LocalDate;
import java.time.LocalTime;

@Component
public class RequestValidator {

    public void validateBirthInfo(LocalDate birthDate, LocalTime birthTime) {
        if (birthDate == null) {
            throw new InvalidSajuDataException(ErrorMessageConstants.BIRTH_DATE_REQUIRED.getMessage());
        }
        if (birthTime == null) {
            throw new InvalidSajuDataException(ErrorMessageConstants.BIRTH_TIME_REQUIRED.getMessage());
        }
    }
}
