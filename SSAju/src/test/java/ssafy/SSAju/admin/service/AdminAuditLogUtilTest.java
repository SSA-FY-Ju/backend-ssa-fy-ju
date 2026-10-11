package ssafy.SSAju.admin.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.read.ListAppender;
import java.time.Clock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import ssafy.SSAju.admin.dto.AdjustmentAction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminAuditLogUtilTest {

    private AdminAuditLogUtil auditLog;
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private final Logger logger = (Logger) LoggerFactory.getLogger(AdminAuditLogUtil.class);
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenThrow(new IllegalStateException("sensitive exception detail"));
        auditLog = new AdminAuditLogUtil(clock);
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:audit-log-failure;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE IF NOT EXISTS audit_operation (id BIGINT PRIMARY KEY)");
        jdbc.update("DELETE FROM audit_operation");
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void recordingFailureDoesNotChangeCommittedOperation() {
        assertThatCode(() -> transaction.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO audit_operation (id) VALUES (1)");
            auditLog.usageAdjusted(1L, AdjustmentAction.RESET, 3, 0);
        })).doesNotThrowAnyException();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_operation", Long.class)).isEqualTo(1L);
        assertRecordingFailure();
    }

    @Test
    void recordingFailureWithoutTransactionDoesNotEscape() {
        assertThatCode(() -> auditLog.loginSucceeded(1L)).doesNotThrowAnyException();
        assertRecordingFailure();
    }

    @Test
    void failureLogExceptionDoesNotEscape() {
        assertThatCode(() -> auditLog.loginFailed("admin@example.com", AdminAuditLogUtil.LoginFailureReason.WRONG_PASSWORD))
                .doesNotThrowAnyException();
        assertRecordingFailure();
    }

    @Test
    void reportingFailureDoesNotEscapeAndRemainsDetectable() {
        Appender<ILoggingEvent> failingAppender = mock(Appender.class);
        doThrow(new IllegalStateException("logger failure")).when(failingAppender).doAppend(any());
        logger.addAppender(failingAppender);
        try {
            assertThatCode(() -> auditLog.loginSucceeded(1L)).doesNotThrowAnyException();
            assertThat(auditLog.recordingFailureCount()).isEqualTo(1);
        } finally {
            logger.detachAppender(failingAppender);
        }
    }

    private void assertRecordingFailure() {
        assertThat(auditLog.recordingFailureCount()).isEqualTo(1);
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getFormattedMessage())
                .contains("recordingFailureCount=1", "exceptionType=IllegalStateException")
                .doesNotContain("sensitive exception detail", "admin@example.com");
    }
}
