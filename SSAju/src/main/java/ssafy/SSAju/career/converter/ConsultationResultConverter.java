package ssafy.SSAju.career.converter;

import jakarta.persistence.Converter;
import ssafy.SSAju.dto.external.CareerAdviceResponse;

@Converter
public class ConsultationResultConverter extends AbstractJsonConverter<CareerAdviceResponse> {

    public ConsultationResultConverter() {
        super(CareerAdviceResponse.class, "CareerAdviceResponse");
    }
}
