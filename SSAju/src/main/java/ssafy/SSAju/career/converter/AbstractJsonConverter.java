package ssafy.SSAju.career.converter;

import jakarta.persistence.AttributeConverter;
import ssafy.SSAju.exception.DataAccessException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * career 도메인 JSON 컬럼 컨버터들의 공통 직렬화/역직렬화 로직.
 *
 * <p>{@link ssafy.SSAju.career.converter.ObjectMapConverter}와 동일한 패턴(H2가 JSON 컬럼 값을
 * 문자열로 한 번 더 감싸 반환하는 경우에 대한 방어적 unwrap 포함)을 담는 타입만 다른 컨버터
 * 3종({@code TenGodHiddenStemConverter}, {@code ConsultationResultConverter},
 * {@code CompatibilityResultConverter})이 공유한다.
 */
public abstract class AbstractJsonConverter<T> implements AttributeConverter<T, String> {

    private static final ObjectMapper MAPPER = CareerJsonObjectMapperSupport.mapper();

    private final Class<T> type;
    private final String typeLabel;

    protected AbstractJsonConverter(Class<T> type, String typeLabel) {
        this.type = type;
        this.typeLabel = typeLabel;
    }

    @Override
    public String convertToDatabaseColumn(T attribute) {
        if (attribute == null) return null;
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new DataAccessException("JSON 직렬화 실패 (" + typeLabel + ")", e);
        }
    }

    @Override
    public T convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            JsonNode node = MAPPER.readTree(dbData);
            if (node.isTextual()) {
                node = MAPPER.readTree(node.textValue());
            }
            return MAPPER.convertValue(node, type);
        } catch (Exception e) {
            throw new DataAccessException("JSON 역직렬화 실패 (" + typeLabel + ")", e);
        }
    }
}
