package ssafy.SSAju.career.converter;

import jakarta.persistence.Converter;
import ssafy.SSAju.career.domain.CompatibilityAnalysisData;

@Converter
public class CompatibilityResultConverter extends AbstractJsonConverter<CompatibilityAnalysisData> {

    public CompatibilityResultConverter() {
        super(CompatibilityAnalysisData.class, "CompatibilityAnalysisData");
    }
}
