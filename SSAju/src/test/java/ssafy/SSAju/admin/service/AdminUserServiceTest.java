package ssafy.SSAju.admin.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ssafy.SSAju.admin.dto.UserSearchDTO;
import ssafy.SSAju.admin.repository.AdminUserQueryRepository;
import ssafy.SSAju.entity.enums.UserStatus;
import ssafy.SSAju.exception.UserNotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminUserService 단위 테스트")
class AdminUserServiceTest {

    @Mock
    private AdminUserQueryRepository userQueryRepository;

    @Mock
    private AdminPaginationUtil paginationUtil;

    @InjectMocks
    private AdminUserService adminUserService;

    private UserSearchDTO stubUser(long id, String email, UserStatus status) {
        return new UserSearchDTO(id, email, "User " + id,
                Instant.now(), status, null, 0L);
    }

    private void stubPagination(int size) {
        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(0, size);
        given(paginationUtil.of(0, size)).willReturn(pageable);
    }

    @Test
    @DisplayName("searchUsers - 필터 없음, 첫 페이지 → 결과 반환")
    void searchUsers_noFilter_returnsUsers() {
        stubPagination(10);
        List<UserSearchDTO> mockList = List.of(
                stubUser(3L, "c@test.com", UserStatus.ACTIVE),
                stubUser(2L, "b@test.com", UserStatus.ACTIVE),
                stubUser(1L, "a@test.com", UserStatus.ACTIVE)
        );
        given(userQueryRepository.findUsersByKeyset(
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(10)))
                .willReturn(mockList);

        List<UserSearchDTO> result = adminUserService.searchUsers(
                null, null, null, null, null, null, 10);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).id()).isEqualTo(3L);
    }

    @Test
    @DisplayName("searchUsers - email 필터 적용 → Repository에 정확히 전달")
    void searchUsers_withEmailFilter_passesToRepository() {
        stubPagination(10);
        given(userQueryRepository.findUsersByKeyset(
                eq("test@"), isNull(), isNull(), isNull(), isNull(), isNull(), anyInt()))
                .willReturn(List.of(stubUser(1L, "test@test.com", UserStatus.ACTIVE)));

        List<UserSearchDTO> result = adminUserService.searchUsers(
                "test@", null, null, null, null, null, 10);

        assertThat(result).hasSize(1);
        verify(userQueryRepository).findUsersByKeyset(
                eq("test@"), isNull(), isNull(), isNull(), isNull(), isNull(), anyInt());
    }

    @Test
    @DisplayName("searchUsers - cursor 전달 → Repository에 cursor 전달")
    void searchUsers_withCursor_passesCursorToRepository() {
        stubPagination(10);
        Long cursor = 50L;
        given(userQueryRepository.findUsersByKeyset(
                isNull(), isNull(), isNull(), isNull(), isNull(), eq(cursor), anyInt()))
                .willReturn(List.of(stubUser(49L, "u@test.com", UserStatus.ACTIVE)));

        List<UserSearchDTO> result = adminUserService.searchUsers(
                null, null, null, null, null, cursor, 10);

        assertThat(result.get(0).id()).isEqualTo(49L);
    }

    @Test
    @DisplayName("searchUsers - Soft Delete 포함 → status INACTIVE 유저도 반환")
    void searchUsers_includesSoftDeletedUsers() {
        stubPagination(10);
        List<UserSearchDTO> mockList = List.of(
                stubUser(2L, "active@test.com", UserStatus.ACTIVE),
                stubUser(1L, "deleted_1@deleted.local", UserStatus.INACTIVE)
        );
        given(userQueryRepository.findUsersByKeyset(
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), anyInt()))
                .willReturn(mockList);

        List<UserSearchDTO> result = adminUserService.searchUsers(
                null, null, null, null, null, null, 10);

        assertThat(result).hasSize(2);
        assertThat(result).anyMatch(u -> u.status() == UserStatus.INACTIVE);
    }

    @Test
    @DisplayName("getUserProfile - 존재하는 userId → UserSearchDTO 반환")
    void getUserProfile_existingUser_returnsDto() {
        UserSearchDTO stub = stubUser(10L, "user@test.com", UserStatus.ACTIVE);
        given(userQueryRepository.findUserById(10L)).willReturn(Optional.of(stub));

        UserSearchDTO result = adminUserService.getUserProfile(10L);

        assertThat(result.id()).isEqualTo(10L);
        assertThat(result.email()).isEqualTo("user@test.com");
    }

    @Test
    @DisplayName("getUserProfile - 존재하지 않는 userId → UserNotFoundException 발생")
    void getUserProfile_notFound_throwsUserNotFoundException() {
        given(userQueryRepository.findUserById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.getUserProfile(999L))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("999");
    }
}
