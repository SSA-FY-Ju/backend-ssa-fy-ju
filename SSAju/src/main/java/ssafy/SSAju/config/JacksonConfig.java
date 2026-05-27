package ssafy.SSAju.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 3.x (tools.jackson) 커스텀 설정.
 *
 * <p>Spring Boot 4.0.5 / Jackson 3.x에서는 {@code SerializationFeature.WRITE_DATES_AS_TIMESTAMPS}가
 * 삭제되어 {@code spring.jackson.serialization.write-dates-as-timestamps=false} yaml 설정 불가.
 * Instant를 "2026-05-27T15:07:31+09:00" 형식으로 직렬화하는 커스텀 모듈로 대체합니다.
 *
 * <p>Spring Boot auto-configuration이 {@code tools.jackson.databind.Module} 빈을
 * 자동으로 감지하여 ObjectMapper에 등록합니다.
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter KST_FORMATTER =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.of("Asia/Seoul"));

    @Bean
    public SimpleModule instantKstSerializerModule() {
        SimpleModule module = new SimpleModule("InstantKstModule");
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
