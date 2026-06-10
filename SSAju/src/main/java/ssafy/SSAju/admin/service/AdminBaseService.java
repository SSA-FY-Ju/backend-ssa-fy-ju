package ssafy.SSAju.admin.service;

import java.time.LocalDate;
import java.time.ZoneId;

public abstract class AdminBaseService {

    public static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    protected LocalDate todaySeoul() {
        return LocalDate.now(SEOUL_ZONE);
    }
}
