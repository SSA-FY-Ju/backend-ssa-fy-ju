package ssafy.SSAju.career.converter;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * career 도메인 JSON 컬럼 컨버터들이 공유하는 {@link ObjectMapper}.
 *
 * <p>{@code config/JacksonConfig}가 애플리케이션 전역 ObjectMapper에 등록하는 Instant KST 포맷과
 * 동일한 포맷을 적용해, JSON 컬럼 내부의 Instant 값과 API 응답의 Instant 값 표현을 일치시킨다.
 */
public final class CareerJsonObjectMapperSupport {

    private static final DateTimeFormatter KST_FORMATTER =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.of("Asia/Seoul"));

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(instantKstSerializerModule())
            .build();

    private CareerJsonObjectMapperSupport() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    private static SimpleModule instantKstSerializerModule() {
        SimpleModule module = new SimpleModule("CareerJsonInstantKstModule");
        module.addSerializer(Instant.class, new StdSerializer<>(Instant.class) {
            @Override
            public void serialize(Instant value, JsonGenerator gen, SerializationContext ctxt)
                    throws JacksonException {
                gen.writeString(KST_FORMATTER.format(value));
            }
        });
        return module;
    }
}
