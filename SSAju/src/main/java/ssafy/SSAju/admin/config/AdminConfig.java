package ssafy.SSAju.admin.config;

import jakarta.validation.Valid;
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
@ConfigurationProperties(prefix = "admin")
public class AdminConfig {

    @Valid
    private Pagination pagination = new Pagination();

    @Valid
    private Api api = new Api();

    @Getter
    @Setter
    public static class Pagination {
        @Min(1) private int defaultPageSize = 20;
        @Min(1) private int maxPageSize = 50;
        @Min(1) private int analyticsRangeDays = 30;
    }

    @Getter
    @Setter
    public static class Api {
        @Min(1) private int dailyLimit = 3;
    }
}
