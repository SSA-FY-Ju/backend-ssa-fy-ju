package ssafy.SSAju.career.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ssafy.SSAju.exception.DataAccessException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Converter
public class ObjectMapConverter implements AttributeConverter<Map<String, Object>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> TYPE_REF = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(Map<String, Object> attribute) {
        if (attribute == null) return null;
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new DataAccessException("JSON 직렬화 실패 (Map<String,Object>)", e);
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            JsonNode node = MAPPER.readTree(dbData);
            // H2 json 컬럼이 JDBC로 반환될 때 값 자체가 JSON 문자열로 한 번 더 래핑되는 경우 대응
            if (node.isTextual()) {
                node = MAPPER.readTree(node.textValue());
            }
            return MAPPER.convertValue(node, TYPE_REF);
        } catch (Exception e) {
            throw new DataAccessException("JSON 역직렬화 실패 (Map<String,Object>)", e);
        }
    }
}
