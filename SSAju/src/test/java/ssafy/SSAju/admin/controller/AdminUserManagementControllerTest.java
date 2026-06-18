package ssafy.SSAju.admin.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ssafy.SSAju.admin.dto.UserSearchDTO;
import ssafy.SSAju.admin.service.AdminUserService;
import ssafy.SSAju.entity.enums.UserStatus;
import ssafy.SSAju.exception.UserNotFoundException;
import ssafy.SSAju.handler.SajuGlobalExceptionHandler;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminUserManagementController HTTP 레이어 테스트")
class AdminUserManagementControllerTest {

    @Mock
    private AdminUserService adminUserService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AdminUserManagementController controller = new AdminUserManagementController(adminUserService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new SajuGlobalExceptionHandler())
                .build();
    }

    private UserSearchDTO stubUser(long id, String email) {
        return new UserSearchDTO(id, email, "User " + id,
                Instant.now(), UserStatus.ACTIVE, null, 0L);
    }

    @Test
    @DisplayName("GET /admin/users/api → 200 + JSON 목록 반환")
    void searchUsersApi_returns200WithList() throws Exception {
        given(adminUserService.searchUsers(
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), anyInt()))
                .willReturn(List.of(
                        stubUser(3L, "c@test.com"),
                        stubUser(2L, "b@test.com")
                ));

        mockMvc.perform(get("/admin/users/api")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(3))
                .andExpect(jsonPath("$[1].id").value(2));
    }

    @Test
    @DisplayName("GET /admin/users/api?cursor=50 → cursor 파라미터 서비스에 전달")
    void searchUsersApi_withCursor_passesToService() throws Exception {
        given(adminUserService.searchUsers(
                isNull(), isNull(), isNull(), isNull(), isNull(), eq(50L), anyInt()))
                .willReturn(List.of(stubUser(49L, "a@test.com")));

        mockMvc.perform(get("/admin/users/api")
                        .param("cursor", "50")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(49));
    }

    @Test
    @DisplayName("GET /admin/users/api?email=test → email 필터 서비스에 전달")
    void searchUsersApi_withEmailFilter_passesToService() throws Exception {
        given(adminUserService.searchUsers(
                eq("test"), isNull(), isNull(), isNull(), isNull(), isNull(), anyInt()))
                .willReturn(List.of(stubUser(1L, "test@test.com")));

        mockMvc.perform(get("/admin/users/api")
                        .param("email", "test")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("test@test.com"));
    }

    @Test
    @DisplayName("GET /admin/users/{id} → 200 + 단일 유저 반환")
    void getUserProfile_existingUser_returns200() throws Exception {
        given(adminUserService.getUserProfile(10L))
                .willReturn(stubUser(10L, "user@test.com"));

        mockMvc.perform(get("/admin/users/10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.email").value("user@test.com"));
    }

    @Test
    @DisplayName("GET /admin/users/{id} → 존재하지 않는 id → 404")
    void getUserProfile_notFound_returns404() throws Exception {
        given(adminUserService.getUserProfile(999L))
                .willThrow(new UserNotFoundException("userId=999"));

        mockMvc.perform(get("/admin/users/999")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
