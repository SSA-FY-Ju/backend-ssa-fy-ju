package ssafy.SSAju.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 비동기 처리 활성화 설정.
 *
 * <p>{@code @EnableAsync}를 선언하여 {@code @Async} 어노테이션이 실제로 동작하도록 합니다.
 * 스레드 풀은 {@code application.yaml}의 {@code spring.task.execution.*}에서 자동 구성됩니다:
 * <ul>
 *   <li>core-size: 5</li>
 *   <li>max-size: 10</li>
 *   <li>queue-capacity: 100</li>
 *   <li>thread-name-prefix: "ssaju-async-"</li>
 * </ul>
 *
 * <p><b>적용 대상:</b>
 * {@link ssafy.SSAju.event.LoginAttemptEventListener#onLoginAttempt} — 로그인 시도 비동기 기록.
 * 이 설정이 없으면 {@code @Async}가 무시되고 로그인 스레드에서 동기 실행됩니다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
    // spring.task.execution.* 설정이 TaskExecutor에 자동 바인딩됨
    // 별도 TaskExecutor 빈 선언 불필요
}
