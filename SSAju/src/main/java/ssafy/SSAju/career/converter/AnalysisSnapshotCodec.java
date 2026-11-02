package ssafy.SSAju.career.converter;

import org.springframework.stereotype.Component;
import ssafy.SSAju.career.domain.AnalysisPayload;
import ssafy.SSAju.career.enums.AnalysisType;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.exception.DataAccessException;
import ssafy.SSAju.exception.InvalidSajuDataException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.EnumMap;
import java.util.Map;

/**
 * 분석 유형별 입력·결과 스냅샷과 {@code saju}의 JSON 컬럼 문자열 사이의 변환기.
 *
 * <p>JPA {@code AttributeConverter}가 아니라 서비스 계층에서 쓰는 컴포넌트다 — 같은 컬럼이라도
 * 행의 {@code analysis_type}에 따라 역직렬화할 record가 달라지므로, 컬럼 단위로 타입이 고정되는
 * AttributeConverter로는 표현할 수 없다.
 *
 * <p>알 수 없는 유형·지원하지 않는 버전·필수값 누락은 저장과 조회 양쪽에서 거부한다. JSON 컬럼이
 * 문법적으로 유효하다는 사실을 업무 유효성으로 간주하지 않는다.
 */
@Component
public class AnalysisSnapshotCodec {

    private static final ObjectMapper MAPPER = CareerJsonObjectMapperSupport.mapper();

    private static final Map<AnalysisType, Class<? extends AnalysisPayload.Input>> INPUT_TYPES =
            new EnumMap<>(Map.of(
                    AnalysisType.SAJU, AnalysisPayload.SajuInput.class,
                    AnalysisType.CAREER_CONSULTATION, AnalysisPayload.ConsultationInput.class,
                    AnalysisType.COMPANY_COMPATIBILITY, AnalysisPayload.CompatibilityInput.class));

    private static final Map<AnalysisType, Class<? extends AnalysisPayload.Result>> RESULT_TYPES =
            new EnumMap<>(Map.of(
                    AnalysisType.SAJU, AnalysisPayload.SajuResultData.class,
                    AnalysisType.CAREER_CONSULTATION, AnalysisPayload.ConsultationResultData.class,
                    AnalysisType.COMPANY_COMPATIBILITY, AnalysisPayload.CompatibilityResultData.class));

    public String writeInput(AnalysisType type, AnalysisPayload.Input input) {
        return write(input, inputTypeOf(type));
    }

    public String writeResult(AnalysisType type, AnalysisPayload.Result result) {
        return write(result, resultTypeOf(type));
    }

    public AnalysisPayload.Input readInput(AnalysisType type, String json) {
        return read(json, inputTypeOf(type));
    }

    public AnalysisPayload.Result readResult(AnalysisType type, String json) {
        return read(json, resultTypeOf(type));
    }

    private Class<? extends AnalysisPayload.Input> inputTypeOf(AnalysisType type) {
        Class<? extends AnalysisPayload.Input> inputType = type == null ? null : INPUT_TYPES.get(type);
        if (inputType == null) {
            throw new InvalidSajuDataException(ErrorMessageConstants.ANALYSIS_TYPE_UNSUPPORTED.getMessage());
        }
        return inputType;
    }

    private Class<? extends AnalysisPayload.Result> resultTypeOf(AnalysisType type) {
        Class<? extends AnalysisPayload.Result> resultType = type == null ? null : RESULT_TYPES.get(type);
        if (resultType == null) {
            throw new InvalidSajuDataException(ErrorMessageConstants.ANALYSIS_TYPE_UNSUPPORTED.getMessage());
        }
        return resultType;
    }

    private <T> String write(T payload, Class<? extends T> expectedType) {
        if (payload == null) {
            throw new InvalidSajuDataException(ErrorMessageConstants.ANALYSIS_SNAPSHOT_REQUIRED.getMessage());
        }
        if (!expectedType.isInstance(payload)) {
            // 유형과 스냅샷 record가 어긋난 채로 저장되면 조회 시점에야 깨진 행으로 드러난다.
            throw new InvalidSajuDataException(ErrorMessageConstants.ANALYSIS_TYPE_UNSUPPORTED.getMessage());
        }
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            throw new DataAccessException(
                    ErrorMessageConstants.ANALYSIS_SNAPSHOT_SERIALIZE_FAILED.getMessage(), e);
        }
    }

    private <T> T read(String json, Class<T> targetType) {
        if (json == null || json.isBlank()) {
            throw new InvalidSajuDataException(ErrorMessageConstants.ANALYSIS_SNAPSHOT_REQUIRED.getMessage());
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            // H2가 JSON 컬럼 값을 문자열로 한 번 더 감싸 반환하는 경우에 대한 방어
            if (node.isTextual()) {
                node = MAPPER.readTree(node.textValue());
            }
            return MAPPER.convertValue(node, targetType);
        } catch (Exception e) {
            InvalidSajuDataException validationFailure = findValidationFailure(e);
            if (validationFailure != null) {
                throw validationFailure;
            }
            throw new DataAccessException(
                    ErrorMessageConstants.ANALYSIS_SNAPSHOT_DESERIALIZE_FAILED.getMessage(), e);
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
