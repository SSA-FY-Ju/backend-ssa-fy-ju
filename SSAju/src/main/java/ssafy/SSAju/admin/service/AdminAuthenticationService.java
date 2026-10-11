package ssafy.SSAju.admin.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.entity.enums.UserRole;
import ssafy.SSAju.admin.repository.AdminUserAuthQueryRepository;
import ssafy.SSAju.admin.enums.AdminLoginFailureReason;
import ssafy.SSAju.annotation.AuditLog;
import ssafy.SSAju.exception.AdminAuthenticationException;

@Service
@RequiredArgsConstructor
public class AdminAuthenticationService {

    private static final String INVALID_CREDENTIALS_MSG = "이메일 또는 비밀번호가 일치하지 않습니다.";
    private static final String ACCESS_DENIED_MSG = "접근 권한이 없습니다.";

    private final AdminUserAuthQueryRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 관리자 자격 증명 검증.
     *
     * <p>이메일/비밀번호를 검증한 뒤 ADMIN 권한 여부를 확인합니다.
     * USER 권한 사용자는 AUTH-003 예외를 발생시킵니다.
     * 반환 admin ID와 인증 예외의 사유를 @AuditLog Aspect가 감사 기록에 사용합니다.
     *
     * @param email 이메일
     * @param password 비밀번호 원문
     * @return 자격 검증이 완료된 admin ID
     * @throws AdminAuthenticationException 이메일 미존재 또는 비밀번호 불일치, ADMIN 권한 없음
     */
    @Transactional(readOnly = true)
    @AuditLog
    public Long validateAdminCredentials(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AdminAuthenticationException(
                        INVALID_CREDENTIALS_MSG, AdminLoginFailureReason.INVALID_EMAIL));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new AdminAuthenticationException(INVALID_CREDENTIALS_MSG, AdminLoginFailureReason.WRONG_PASSWORD);
        }

        if (user.getRole() != UserRole.ADMIN) {
            throw new AdminAuthenticationException(ACCESS_DENIED_MSG, AdminLoginFailureReason.NOT_ADMIN);
        }

        return user.getId();
    }
}
