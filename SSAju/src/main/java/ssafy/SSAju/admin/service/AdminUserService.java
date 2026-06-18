package ssafy.SSAju.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.admin.dto.UserSearchDTO;
import ssafy.SSAju.admin.repository.AdminUserQueryRepository;
import ssafy.SSAju.entity.enums.UserStatus;
import ssafy.SSAju.exception.UserNotFoundException;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserService extends AdminBaseService {

    private final AdminUserQueryRepository userQueryRepository;
    private final AdminPaginationUtil paginationUtil;

    public Page<UserSearchDTO> searchUsers(
            String email, String name,
            Instant joinDateFrom, Instant joinDateTo,
            UserStatus status, int page, int size) {

        Pageable pageable = paginationUtil.of(page, size);
        Page<UserSearchDTO> result = userQueryRepository.findUsersByFilters(
                email, name, joinDateFrom, joinDateTo, status, pageable);

        log.debug("유저 검색 완료: email={}, page={}, totalElements={}", email, page, result.getTotalElements());
        return result;
    }

    public UserSearchDTO getUserProfile(Long userId) {
        return userQueryRepository.findUserById(userId)
                .orElseThrow(() -> new UserNotFoundException("userId=" + userId));
    }
}
