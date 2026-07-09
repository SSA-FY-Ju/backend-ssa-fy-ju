# Contract: 관리자 인증 감사로그 (User Story 8, G1)

## `POST /admin/login` (`AdminAuthenticationService.validateAdminCredentials`)

**Before**: `repository/UserRepository`(공용 리포지토리) 사용, 성공/실패 시 감사로그 기록 없음.

**After**:
- 리포지토리: admin 전용 리포지토리(`admin/repository/*`)를 통해 사용자 조회(C4 — admin 서비스 계층은 admin 전용 리포지토리만 사용).
- 성공 경로: 감사로그 이벤트 기록(관리자 이메일 또는 ID, 성공 여부=성공, 타임스탬프는 주입된 `Clock` 사용) — 트랜잭션 커밋 이후 기록(기존 `AdminUsageAdjustmentService`의 "완료 로그를 커밋 이후로 지연" 패턴과 동일하게 G2 공통 유틸 사용).
- 실패 경로(자격 불일치, ADMIN 권한 아님): 감사로그 이벤트 기록(시도한 이메일, 실패 사유 코드, 타임스탬프) — 실패는 인증 예외 발생 이전/직후 즉시 기록(실패 시 트랜잭션 롤백과 무관하게 감사로그 자체는 남아야 하므로 로그 기록은 별도 트랜잭션 또는 트랜잭션 밖에서 처리).
- 응답 스키마/상태 코드 변경 없음 — 감사로그는 부가 효과(side effect)이며 API 계약에 노출되지 않음.
