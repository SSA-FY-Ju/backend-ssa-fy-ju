package ssafy.SSAju.career.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ssafy.SSAju.career.domain.ManseData;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.exception.DataAccessException;
import ssafy.SSAju.exception.InvalidSajuDataException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code manse.data_json} 컬럼 ↔ {@link ManseData} 변환기.
 *
 * <p>{@link AbstractJsonConverter}와 달리 역직렬화 실패를 무조건 {@link DataAccessException}으로
 * 감싸지 않는다 — {@link ManseData}의 생성자 검증(버전·필수값·배열 길이·허용값)에서 나온
 * {@link InvalidSajuDataException}은 "저장 형식이 깨졌다"가 아니라 "사주 데이터가 유효하지 않다"는
 * 뜻이므로 원래 의미 그대로 전파해야 기존 오류 체계와 일치한다.
 */
@Converter
public class ManseDataConverter implements AttributeConverter<ManseData, String> {

    private static final ObjectMapper MAPPER = CareerJsonObjectMapperSupport.mapper();

    @Override
    public String convertToDatabaseColumn(ManseData attribute) {
        if (attribute == null) {
            throw new InvalidSajuDataException(ErrorMessageConstants.MANSE_DATA_REQUIRED.getMessage());
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new DataAccessException(
                    ErrorMessageConstants.ANALYSIS_SNAPSHOT_SERIALIZE_FAILED.getMessage(), e);
        }
    }

    @Override
    public ManseData convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            throw new InvalidSajuDataException(ErrorMessageConstants.MANSE_DATA_REQUIRED.getMessage());
        }
        try {
            JsonNode node = MAPPER.readTree(dbData);
            // H2가 JSON 컬럼 값을 문자열로 한 번 더 감싸 반환하는 경우에 대한 방어 (ObjectMapConverter와 동일)
            if (node.isTextual()) {
                node = MAPPER.readTree(node.textValue());
            }
            return MAPPER.convertValue(node, ManseData.class);
        } catch (Exception e) {
            InvalidSajuDataException validationFailure = findValidationFailure(e);
            if (validationFailure != null) {
                throw validationFailure;
            }
            throw new DataAccessException(
                    ErrorMessageConstants.MANSE_DATA_REQUIRED.getMessage(), e);
        }
    }

    private InvalidSajuDataException findValidationFailure(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof InvalidSajuDataException invalid) {
                return invalid;
            }
            if (current.getCause() == current) {
                return null;
            }
        }
        return null;
    }
}
