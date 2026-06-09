package ssafy.SSAju.admin.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
public class AdminPaginationUtil {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    public Pageable of(int page, int size) {
        int validSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int validPage = Math.max(page, 0);
        return PageRequest.of(validPage, validSize);
    }

    public Pageable ofDefault(int page) {
        return PageRequest.of(Math.max(page, 0), DEFAULT_PAGE_SIZE);
    }
}
