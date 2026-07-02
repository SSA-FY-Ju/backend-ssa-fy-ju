package ssafy.SSAju.admin.validation;

import org.springframework.stereotype.Component;
import ssafy.SSAju.career.enums.AnalysisType;
import ssafy.SSAju.entity.enums.UserStatus;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * [향후 리팩토링 예정]
 * 현재 admin/validation 패키지에 격리된 상태로 유지됩니다.
 * {@code AdminAnalyticsController}, {@code AdminUserManagementController} 등에서
 * type/status 파라미터를 검증 없이 String 그대로 사용하는 곳이 있어,
 * 다음 리팩토링 사이클에서 해당 컨트롤러/서비스에 이 Validator 적용을 검토해야 합니다.
 */
@Component
public class FilterValidator {

    public void validateAnalysisType(String type) {
        if (type == null) {
            return;
        }
        boolean valid = Arrays.stream(AnalysisType.values())
                .anyMatch(t -> t.name().equals(type));
        if (!valid) {
            throw new IllegalArgumentException(
                    "허용되지 않는 analysisType입니다: " + type + ", 허용값: " + allowedValues(AnalysisType.values()));
        }
    }

    public void validateUserStatus(String status) {
        if (status == null) {
            return;
        }
        boolean valid = Arrays.stream(UserStatus.values())
                .anyMatch(s -> s.name().equals(status));
        if (!valid) {
            throw new IllegalArgumentException(
                    "허용되지 않는 status입니다: " + status + ", 허용값: " + allowedValues(UserStatus.values()));
        }
    }

    private String allowedValues(Enum<?>[] values) {
        return Arrays.stream(values)
                .map(Enum::name)
                .collect(Collectors.joining(", "));
    }
}
