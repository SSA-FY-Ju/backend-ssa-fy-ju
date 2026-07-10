package ssafy.SSAju.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Configuration
@ConfigurationProperties(prefix = "redisson.lock")
public class RedissonLockProperties {

    @Min(1)
    private long waitTimeMs = 2000;

    @Min(1)
    private long leaseTimeMs = 5000;
}
