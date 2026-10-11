package ssafy.SSAju.admin.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import ssafy.SSAju.admin.dto.AdjustmentAction;
import ssafy.SSAju.annotation.AuditLog;
import ssafy.SSAju.admin.repository.AdminUserAuthQueryRepository;
import ssafy.SSAju.aspect.AdminAuthenticationAuditAspect;
import ssafy.SSAju.aspect.AuditLoggingAspect;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.entity.enums.UserRole;
import ssafy.SSAju.exception.AuthException;
import ssafy.SSAju.util.TokenHashUtil;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(AdminAuthenticationServiceAuditTest.Config.class)
class AdminAuthenticationServiceAuditTest {

    private static final String EMAIL = "admin@example.com";
    private static final String PASSWORD = "secret-password";
    private static final Instant NOW = Instant.parse("2026-10-10T00:00:00Z");

    @Autowired private AdminAuthenticationService service;
    @Autowired private AdminUserAuthQueryRepository repository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private AdminAuditLogUtil auditLog;
    @Autowired private Clock clock;
    @Autowired private OrdinaryAuditedService ordinaryService;

    private final Logger logger = (Logger) LoggerFactory.getLogger(AdminAuditLogUtil.class);
    private final Logger commonLogger = (Logger) LoggerFactory.getLogger(AuditLoggingAspect.class);
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        reset(repository, passwordEncoder, clock);
        when(clock.instant()).thenReturn(NOW);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        commonLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        commonLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void successIsRecordedOnceAfterCommit() {
        givenUser(UserRole.ADMIN, true);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(service.validateAdminCredentials(EMAIL, PASSWORD)).isEqualTo(7L);
            assertThat(appender.list).isEmpty();
        });
        assertThat(appender.list).hasSize(1);
        assertThat(message()).contains("action=validateAdminCredentials", "userId=7", "status=SUCCESS",
                "timestamp=" + NOW).doesNotContain(EMAIL, PASSWORD);
    }

    @Test
    void ordinaryAuditAnnotationStillUsesCommonAspect() {
        ordinaryService.ordinaryAction(11L);
        assertThat(appender.list).hasSize(1);
        assertThat(message()).contains("action=ordinaryAction", "userId=11", "status=SUCCESS");
    }

    @Test
    void auditFailureDoesNotChangeAuthenticationSuccess() {
        givenUser(UserRole.ADMIN, true);
        when(clock.instant()).thenThrow(new IllegalStateException("sensitive detail"));
        long failuresBefore = auditLog.recordingFailureCount();
        assertThat(service.validateAdminCredentials(EMAIL, PASSWORD)).isEqualTo(7L);
        assertThat(auditLog.recordingFailureCount()).isEqualTo(failuresBefore + 1);
        assertThat(appender.list).hasSize(1);
        assertThat(message()).contains("exceptionType=IllegalStateException").doesNotContain("sensitive detail");
    }

    @Test
    void auditFailurePreservesOriginalAuthenticationException() {
        when(repository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(clock.instant()).thenThrow(new IllegalStateException("sensitive detail"));
        long failuresBefore = auditLog.recordingFailureCount();
        assertThatThrownBy(() -> service.validateAdminCredentials(EMAIL, PASSWORD))
                .isInstanceOf(AuthException.class).hasMessage("이메일 또는 비밀번호가 일치하지 않습니다.");
        assertThat(auditLog.recordingFailureCount()).isEqualTo(failuresBefore + 1);
        assertThat(appender.list).hasSize(1);
    }

    @Test
    void successIsNotRecordedWhenOuterTransactionRollsBack() {
        givenUser(UserRole.ADMIN, true);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.validateAdminCredentials(EMAIL, PASSWORD);
            status.setRollbackOnly();
        });
        assertThat(appender.list).isEmpty();
    }

    @Test
    void commitFailureDoesNotRecordSuccess() {
        givenUser(UserRole.ADMIN, true);
        TestTransactionManager manager = (TestTransactionManager) transactionManager;
        manager.failCommit = true;
        try {
            assertThatThrownBy(() -> service.validateAdminCredentials(EMAIL, PASSWORD))
                    .isInstanceOf(TransactionSystemException.class);
            assertThat(appender.list).isEmpty();
        } finally {
            manager.failCommit = false;
        }
    }

    @Test
    void usageAdjustmentCompletionIsRecordedAfterCommit() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            auditLog.usageAdjusted(7L, AdjustmentAction.RESET, 3, 0);
            assertThat(appender.list).isEmpty();
        });
        assertThat(appender.list).hasSize(1);
        assertThat(message()).contains("일일 사용량 조정 완료", "userId=7", "before=3", "after=0", "timestamp=" + NOW);
    }

    @Test
    void usageAdjustmentRollbackDoesNotRecordCompletion() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            auditLog.usageAdjusted(7L, AdjustmentAction.RESET, 3, 0);
            status.setRollbackOnly();
        });
        assertThat(appender.list).isEmpty();
    }

    @Test
    void usageAdjustmentWithoutTransactionRecordsImmediately() {
        auditLog.usageAdjusted(7L, AdjustmentAction.RESET, 3, 0);
        assertThat(appender.list).hasSize(1);
    }

    @Test
    void missingEmailIsRecordedImmediatelyAndSurvivesRollback() {
        when(repository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            try {
                service.validateAdminCredentials(EMAIL, PASSWORD);
            } finally {
                assertThat(appender.list).hasSize(1);
            }
        })).isInstanceOf(AuthException.class).hasMessage("이메일 또는 비밀번호가 일치하지 않습니다.");
        assertFailure("INVALID_EMAIL");
    }

    @Test
    void wrongPasswordIsRecordedWithoutCredentials() {
        givenUser(UserRole.ADMIN, false);
        assertThatThrownBy(() -> service.validateAdminCredentials(EMAIL, PASSWORD))
                .isInstanceOf(AuthException.class).hasMessage("이메일 또는 비밀번호가 일치하지 않습니다.");
        assertFailure("WRONG_PASSWORD");
    }

    @Test
    void nonAdminIsRecordedWithoutChangingAccessDeniedResponse() {
        givenUser(UserRole.USER, true);
        assertThatThrownBy(() -> service.validateAdminCredentials(EMAIL, PASSWORD))
                .isInstanceOf(AuthException.class).hasMessage("접근 권한이 없습니다.");
        assertFailure("NOT_ADMIN");
    }

    private void givenUser(UserRole role, boolean passwordMatches) {
        User user = mock(User.class);
        when(user.getPasswordHash()).thenReturn("stored-hash");
        when(passwordEncoder.matches(PASSWORD, "stored-hash")).thenReturn(passwordMatches);
        if (passwordMatches) when(user.getRole()).thenReturn(role);
        if (passwordMatches && role == UserRole.ADMIN) when(user.getId()).thenReturn(7L);
        when(repository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }

    private void assertFailure(String reason) {
        assertThat(appender.list).hasSize(1);
        assertThat(message()).contains("action=validateAdminCredentials", "status=FAILURE", "reason=" + reason,
                "emailHash=" + TokenHashUtil.sha256(EMAIL), "timestamp=" + NOW)
                .doesNotContain(EMAIL, PASSWORD, "stored-hash");
    }

    private String message() {
        return appender.list.getFirst().getFormattedMessage();
    }

    @Configuration
    @EnableTransactionManagement
    @EnableAspectJAutoProxy
    static class Config {
        @Bean AdminUserAuthQueryRepository repository() { return mock(AdminUserAuthQueryRepository.class); }
        @Bean PasswordEncoder passwordEncoder() { return mock(PasswordEncoder.class); }
        @Bean Clock clock() { return mock(Clock.class); }
        @Bean AdminAuditLogUtil auditLog(Clock clock) { return new AdminAuditLogUtil(clock); }
        @Bean AdminAuthenticationService service(AdminUserAuthQueryRepository repository,
                PasswordEncoder passwordEncoder) {
            return new AdminAuthenticationService(repository, passwordEncoder);
        }
        @Bean AdminAuthenticationAuditAspect adminAuditAspect(AdminAuditLogUtil auditLog) {
            return new AdminAuthenticationAuditAspect(auditLog);
        }
        @Bean AuditLoggingAspect commonAuditAspect() { return new AuditLoggingAspect(); }
        @Bean OrdinaryAuditedService ordinaryService() { return new OrdinaryAuditedService(); }
        @Bean PlatformTransactionManager transactionManager() {
            return new TestTransactionManager();
        }
    }

    static class OrdinaryAuditedService {
        @AuditLog
        public void ordinaryAction(Long userId) {
        }
    }

    static class TestTransactionManager extends DataSourceTransactionManager {
        boolean failCommit;

        TestTransactionManager() {
            super(new DriverManagerDataSource("jdbc:h2:mem:admin-audit", "sa", ""));
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            if (failCommit) throw new TransactionSystemException("test commit failure");
            super.doCommit(status);
        }
    }
}
