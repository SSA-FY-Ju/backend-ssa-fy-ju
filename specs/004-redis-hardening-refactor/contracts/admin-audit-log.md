# Contract: 관리자 인증 감사로그 (User Story 8, G1)

## `POST /admin/login` (`AdminAuthenticationService.validateAdminCredentials`)

**Before**: `repository/UserRepository`(공용 리포지토리) 사용, 성공/실패 시 감사로그 기록 없음.

**After**:
- 리포지토리: admin 전용 리포지토리(`admin/repository/*`)를 통해 사용자 조회(C4 — admin 서비스 계층은 admin 전용 리포지토리만 사용).
- 성공 경로: 감사로그 이벤트 기록(내부 admin ID만 — 개인정보 최소화), 성공 여부=성공, 타임스탬프는 주입된 `Clock` 사용) — 트랜잭션 커밋 이후 기록(기존 `AdminUsageAdjustmentService`의 "완료 로그를 커밋 이후로 지연" 패턴과 동일하게 G2 공통 유틸 사용).
- 실패 경로(자격 불일치, ADMIN 권한 아님): 감사로그 이벤트 기록(시도한 이메일은 마스킹/해시된 식별자로만 기록, 실패 사유 코드, 타임스탬프) — 실패는 인증 예외 발생 이전/직후 즉시 기록(실패 시 트랜잭션 롤백과 무관하게 감사로그 자체는 남아야 하므로 로그 기록은 별도 트랜잭션 또는 트랜잭션 밖에서 처리).
- 응답 스키마/상태 코드 변경 없음 — 감사로그는 부가 효과(side effect)이며 API 계약에 노출되지 않음.


## 구현 및 검증

- `AdminUserAuthQueryRepository.findByEmail()`은 Spring Data 파생 조회를 사용해 기존 탈퇴 사용자 제외 조건을 유지한다.
- `AdminAuditLogUtil`은 SLF4J 구조화 로그를 기록한다. 별도의 감사로그 테이블이나 조회 API는 추가하지 않는다.
- 성공 로그: `[AUDIT] action=validateAdminCredentials, userId=<내부 ID>, status=SUCCESS, timestamp=<Clock 시각>`.
- 실패 로그: `[AUDIT] action=validateAdminCredentials, emailHash=<SHA-256>, status=FAILURE, reason=INVALID_EMAIL|WRONG_PASSWORD|NOT_ADMIN, timestamp=<Clock 시각>`. 비밀번호와 이메일 원문은 기록하지 않는다.
- 성공 로그는 바깥 트랜잭션까지 커밋된 경우에만 기록한다. 인증 예외 로그는 즉시 남겨 롤백으로 사라지지 않는다.
- `@AuditLog`의 일반적인 인자 추출로는 이 계약의 식별자/사유 코드를 얻을 수 없어 해당 인증 메서드에서 공통 유틸을 직접 호출한다. 감사 대상은 자격 검증이며 토큰 발급 결과 및 DTO 검증 오류는 포함하지 않는다.
- `AdminAuthenticationServiceAuditTest`에서 실제 Spring 트랜잭션 프록시와 H2 트랜잭션을 사용해 성공 지연/롤백/커밋 실패, 세 가지 인증 실패, 원문 비노출, 사용량 조정 완료 로그의 커밋/롤백을 검증한다.
