package ssafy.SSAju.career.validator;

import org.springframework.stereotype.Component;
import ssafy.SSAju.career.domain.HiddenStems;
import ssafy.SSAju.career.enums.ErrorMessageConstants;

@Component
public class CompatibilityValidator {

    public void validate(HiddenStems userHiddenStems, String userDayMaster,
                         HiddenStems companyHiddenStems, String companyDayMaster) {
        if (userHiddenStems == null) {
            throw new IllegalArgumentException(ErrorMessageConstants.USER_HIDDEN_STEM_NULL.getMessage());
        }
        if (userDayMaster == null || userDayMaster.isBlank()) {
            throw new IllegalArgumentException(ErrorMessageConstants.USER_DAY_MASTER_NULL.getMessage());
        }
        if (companyHiddenStems == null) {
            throw new IllegalArgumentException(ErrorMessageConstants.COMPANY_HIDDEN_STEM_NULL.getMessage());
        }
        if (companyDayMaster == null || companyDayMaster.isBlank()) {
            throw new IllegalArgumentException(ErrorMessageConstants.COMPANY_DAY_MASTER_NULL.getMessage());
        }
    }
}
