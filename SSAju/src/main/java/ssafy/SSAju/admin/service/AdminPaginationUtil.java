package ssafy.SSAju.admin.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import ssafy.SSAju.admin.config.AdminConfig;

@Component
@RequiredArgsConstructor
public class AdminPaginationUtil {

    private final AdminConfig adminConfig;

    public Pageable of(int page, int size) {
        int maxSize = adminConfig.getPagination().getMaxPageSize();
        int validSize = Math.min(Math.max(size, 1), maxSize);
        int validPage = Math.max(page, 0);
        return PageRequest.of(validPage, validSize);
    }

    public Pageable ofDefault(int page) {
        int defaultSize = adminConfig.getPagination().getDefaultPageSize();
        int validSize = Math.max(defaultSize, 1);
        return PageRequest.of(Math.max(page, 0), validSize);
    }
}
