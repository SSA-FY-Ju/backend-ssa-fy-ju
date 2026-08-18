package ssafy.SSAju.career.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ssafy.SSAju.career.domain.TenGodHiddenStemAnalysis;
import ssafy.SSAju.exception.DataAccessException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Converter
public class TenGodHiddenStemConverter implements AttributeConverter<TenGodHiddenStemAnalysis, String> {

    private static final ObjectMapper MAPPER = CareerJsonObjectMapperSupport.mapper();

    @Override
    public String convertToDatabaseColumn(TenGodHiddenStemAnalysis attribute) {
        if (attribute == null) return null;
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new DataAccessException("JSON 직렬화 실패 (TenGodHiddenStemAnalysis)", e);
        }
    }

    @Override
    public TenGodHiddenStemAnalysis convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            JsonNode node = MAPPER.readTree(dbData);
            if (node.isTextual()) {
                node = MAPPER.readTree(node.textValue());
            }
            return MAPPER.convertValue(node, TenGodHiddenStemAnalysis.class);
        } catch (Exception e) {
            throw new DataAccessException("JSON 역직렬화 실패 (TenGodHiddenStemAnalysis)", e);
        }
    }
}
