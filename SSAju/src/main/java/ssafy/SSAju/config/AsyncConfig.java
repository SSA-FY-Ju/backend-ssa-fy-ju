package ssafy.SSAju.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 비동기 처리 설정.
 *
 * {@code @Async} 어노테이션을 통한 비동기 메서드 실행을 활성화합니다.
 * 로그인 시도 기록, 메일 발송 등 블로킹 작업을 비동기로 처리하여
 * 메인 요청 흐름을 방해하지 않도록 합니다.
 *
 * @see org.springframework.scheduling.annotation.Async
 * @see ssafy.SSAju.event.LoginAttemptEventListener
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
