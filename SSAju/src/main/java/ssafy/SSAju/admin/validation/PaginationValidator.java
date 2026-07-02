package ssafy.SSAju.admin.validation;

import org.springframework.stereotype.Component;

/**
 * [향후 리팩토링 예정]
 * 현재 admin/validation 패키지에 격리된 상태로 유지됩니다.
 * {@code AdminPaginationUtil}은 잘못된 page/size 값을 예외 없이 clamp(보정)하는 방식이라
 * 이 클래스의 "명시적 예외 발생" 방식과 검증 전략이 다릅니다.
 * 다음 리팩토링 사이클에서 두 방식 중 하나로 통일할지 검토가 필요합니다.
 */
@Component
public class PaginationValidator {

    public void validate(int page, int size, int maxSize) {
        if (page < 0) {
            throw new IllegalArgumentException("page는 0 이상이어야 합니다: page=" + page);
        }
        if (size <= 0) {
            throw new IllegalArgumentException("size는 1 이상이어야 합니다: size=" + size);
        }
        if (size > maxSize) {
            throw new IllegalArgumentException("size는 " + maxSize + " 이하여야 합니다: size=" + size);
        }
    }
}
