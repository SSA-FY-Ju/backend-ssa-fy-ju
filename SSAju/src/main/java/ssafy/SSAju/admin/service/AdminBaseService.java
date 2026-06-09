package ssafy.SSAju.admin.service;

import java.time.LocalDate;
import java.time.ZoneId;

public abstract class AdminBaseService {

    protected static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    protected static final int DAILY_API_LIMIT = 3;

    protected LocalDate todaySeoul() {
        return LocalDate.now(SEOUL_ZONE);
    }
}
