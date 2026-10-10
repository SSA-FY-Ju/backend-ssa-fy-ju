package ssafy.SSAju.admin.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.entity.enums.UserRole;
import ssafy.SSAju.exception.AuthException;
import ssafy.SSAju.admin.repository.AdminUserAuthQueryRepository;
import ssafy.SSAju.admin.service.AdminAuditLogUtil.LoginFailureReason;

@Service
@RequiredArgsConstructor
public class AdminAuthenticationService {

    private static final String INVALID_CREDENTIALS_MSG = "이메일 또는 비밀번호가 일치하지 않습니다.";
    private static final String ACCESS_DENIED_MSG = "접근 권한이 없습니다.";

    private final AdminUserAuthQueryRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminAuditLogUtil auditLog;

    /**
     * 관리자 자격 증명 검증.
     *
     * <p>이메일/비밀번호를 검증한 뒤 ADMIN 권한 여부를 확인합니다.
     * USER 권한 사용자는 AUTH-003 예외를 발생시킵니다.
     * 감사로그는 admin ID와 실패 사유를 담기 위해 공통 @AuditLog 대신 AdminAuditLogUtil로 기록합니다.
     *
     * @param email 이메일
     * @param password 비밀번호 원문
     * @throws AuthException 이메일 미존재 또는 비밀번호 불일치 (AUTH-002)
     * @throws AuthException ADMIN 권한이 없는 사용자 (AUTH-003)
     */
    @Transactional(readOnly = true)
    public void validateAdminCredentials(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    auditLog.loginFailed(email, LoginFailureReason.INVALID_EMAIL);
                    return new AuthException(INVALID_CREDENTIALS_MSG);
                });

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            auditLog.loginFailed(email, LoginFailureReason.WRONG_PASSWORD);
            throw new AuthException(INVALID_CREDENTIALS_MSG);
        }

        if (user.getRole() != UserRole.ADMIN) {
            auditLog.loginFailed(email, LoginFailureReason.NOT_ADMIN);
            throw new AuthException(ACCESS_DENIED_MSG);
        }

        auditLog.loginSucceeded(user.getId());
    }
}
