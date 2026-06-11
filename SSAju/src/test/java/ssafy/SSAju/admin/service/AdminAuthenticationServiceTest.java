package ssafy.SSAju.admin.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.entity.enums.UserRole;
import ssafy.SSAju.entity.enums.UserStatus;
import ssafy.SSAju.exception.AuthException;
import ssafy.SSAju.repository.UserRepository;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminAuthenticationService 단위 테스트")
class AdminAuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AdminAuthenticationService adminAuthenticationService;

    private User buildUser(String email, UserRole role) {
        return User.builder()
                .email(email)
                .passwordHash("hashed")
                .name("관리자")
                .role(role)
                .status(UserStatus.ACTIVE)
                .termsAgreedAt(Instant.now())
                .privacyAgreedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("ADMIN 권한 사용자 + 올바른 비밀번호 → 정상 통과")
    void validateAdminCredentials_adminUser_success() {
        // Given
        User adminUser = buildUser("admin@test.com", UserRole.ADMIN);
        given(userRepository.findByEmail("admin@test.com")).willReturn(Optional.of(adminUser));
        given(passwordEncoder.matches("password", "hashed")).willReturn(true);

        // When & Then
        assertThatCode(() -> adminAuthenticationService.validateAdminCredentials("admin@test.com", "password"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("USER 권한 사용자 → AUTH-003 예외 발생")
    void validateAdminCredentials_userRole_throwsAuthException() {
        // Given
        User normalUser = buildUser("user@test.com", UserRole.USER);
        given(userRepository.findByEmail("user@test.com")).willReturn(Optional.of(normalUser));
        given(passwordEncoder.matches("password", "hashed")).willReturn(true);

        // When & Then
        assertThatThrownBy(() -> adminAuthenticationService.validateAdminCredentials("user@test.com", "password"))
                .isInstanceOf(AuthException.class)
                .hasMessage("접근 권한이 없습니다.");
    }

    @Test
    @DisplayName("이메일 미존재 → 자격증명 불일치 예외 발생")
    void validateAdminCredentials_emailNotFound_throwsAuthException() {
        // Given
        given(userRepository.findByEmail("unknown@test.com")).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> adminAuthenticationService.validateAdminCredentials("unknown@test.com", "password"))
                .isInstanceOf(AuthException.class)
                .hasMessage("이메일 또는 비밀번호가 일치하지 않습니다.");
    }

    @Test
    @DisplayName("비밀번호 불일치 → 자격증명 불일치 예외 발생")
    void validateAdminCredentials_wrongPassword_throwsAuthException() {
        // Given
        User adminUser = buildUser("admin@test.com", UserRole.ADMIN);
        given(userRepository.findByEmail("admin@test.com")).willReturn(Optional.of(adminUser));
        given(passwordEncoder.matches("wrong", "hashed")).willReturn(false);

        // When & Then
        assertThatThrownBy(() -> adminAuthenticationService.validateAdminCredentials("admin@test.com", "wrong"))
                .isInstanceOf(AuthException.class)
                .hasMessage("이메일 또는 비밀번호가 일치하지 않습니다.");
    }
}
