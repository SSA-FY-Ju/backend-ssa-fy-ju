package ssafy.SSAju.admin.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "admin")
public class AdminConfig {

    private Pagination pagination = new Pagination();
    private Api api = new Api();

    @Getter
    @Setter
    public static class Pagination {
        private int defaultPageSize = 20;
        private int maxPageSize = 50;
        private int analyticsRangeDays = 30;
    }

    @Getter
    @Setter
    public static class Api {
        private int dailyLimit = 3;
    }
}
