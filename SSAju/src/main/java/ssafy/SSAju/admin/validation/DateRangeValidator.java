package ssafy.SSAju.admin.validation;

import org.springframework.stereotype.Component;
import ssafy.SSAju.exception.InvalidDateRangeException;

import java.time.LocalDate;

/**
 * [향후 리팩토링 예정]
 * 현재 admin/validation 패키지에 격리된 상태로 유지됩니다.
 * {@code AdminAnalyticsService.validateDateRange}에 동일한 검증 로직이
 * 이미 인라인으로 존재하므로, 다음 리팩토링 사이클에서 해당 로직을
 * 이 클래스로 통합할지 검토가 필요합니다.
 */
@Component
public class DateRangeValidator {

    public void validate(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            return;
        }
        if (from.isAfter(to)) {
            throw new InvalidDateRangeException("시작일이 종료일보다 늦을 수 없습니다.");
        }
    }
}
