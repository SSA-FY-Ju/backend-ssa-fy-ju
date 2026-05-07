package ssafy.SSAju.career.validator;

import org.springframework.stereotype.Component;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.dto.external.FastAPIResponse;
import ssafy.SSAju.exception.InvalidSajuDataException;

import java.util.List;
import java.util.Map;

@Component
public class SajuValidator {

    public void validate(FastAPIResponse sajuData) {
        if (sajuData == null) {
            throw new InvalidSajuDataException(ErrorMessageConstants.SAJU_DATA_NULL.getMessage());
        }
        List<String> heavenlyStems = sajuData.heavenlyStems();
        if (heavenlyStems == null || heavenlyStems.size() != 4) {
            throw new InvalidSajuDataException(ErrorMessageConstants.HEAVENLY_STEMS_COUNT_INVALID.getMessage());
        }
        List<String> earthlyBranches = sajuData.earthlyBranches();
        if (earthlyBranches == null || earthlyBranches.size() != 4) {
            throw new InvalidSajuDataException(ErrorMessageConstants.EARTHLY_BRANCHES_COUNT_INVALID.getMessage());
        }
    }

    public void validateWithFiveElements(FastAPIResponse sajuData) {
        validate(sajuData);
        Map<String, Integer> fiveElements = sajuData.fiveElements();
        if (fiveElements == null || fiveElements.isEmpty()) {
            throw new InvalidSajuDataException(ErrorMessageConstants.FIVE_ELEMENTS_REQUIRED.getMessage());
        }
    }
}
