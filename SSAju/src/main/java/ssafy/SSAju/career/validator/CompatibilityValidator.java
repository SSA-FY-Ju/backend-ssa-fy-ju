package ssafy.SSAju.career.validator;

import org.springframework.stereotype.Component;
import ssafy.SSAju.career.domain.HiddenStems;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.exception.InvalidSajuDataException;

@Component
public class CompatibilityValidator {

    public void validate(HiddenStems userHiddenStems, String userDayMaster,
                         HiddenStems companyHiddenStems, String companyDayMaster) {
        if (userHiddenStems == null) {
            throw new InvalidSajuDataException(ErrorMessageConstants.USER_HIDDEN_STEM_NULL.getMessage());
        }
        if (userDayMaster == null || userDayMaster.isBlank()) {
            throw new InvalidSajuDataException(ErrorMessageConstants.USER_DAY_MASTER_NULL.getMessage());
        }
        if (companyHiddenStems == null) {
            throw new InvalidSajuDataException(ErrorMessageConstants.COMPANY_HIDDEN_STEM_NULL.getMessage());
        }
        if (companyDayMaster == null || companyDayMaster.isBlank()) {
            throw new InvalidSajuDataException(ErrorMessageConstants.COMPANY_DAY_MASTER_NULL.getMessage());
        }
    }
}
