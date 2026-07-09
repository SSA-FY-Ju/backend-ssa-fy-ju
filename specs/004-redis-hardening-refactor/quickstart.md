# Quickstart: 검증 가이드

## 사전 준비

- Docker(로컬 MySQL + Redis, 통합 테스트용 Testcontainers)
- `SSAju/src/main/resources/application-local.yaml`(gitignored)에 `DB_URL/DB_USERNAME/DB_PASSWORD`, `JWT_SECRET`, `FASTAPI_URL` 등 기존 필수값 + 신규 `REDIS_HOST`/`REDIS_PORT`(또는 `spring.data.redis.*`) 설정
- 로컬 Redis 실행: `docker run -p 6379:6379 redis:7-alpine`

## 빌드 및 테스트

```bash
cd SSAju
./gradlew test                 # 전체 단위/통합 테스트 (H2 + MySQL/Redis Testcontainers)
./gradlew test --tests "*concurrency*"   # 분산락 동시성 재현 테스트만
./gradlew bootRun               # 로컬 실행 (local 프로파일)
```

## 시나리오별 수동 검증 (spec.md 승인 시나리오 대응)

### US1 — 세션 보안 (로그아웃 후 토큰 재사용 불가)

1. `POST /api/auth/login` → 쿠키(`refreshToken`)와 응답의 `accessToken` 확보 ([contracts/auth-token-endpoints.md](./contracts/auth-token-endpoints.md) 참고).
2. 해당 `accessToken`으로 보호된 API(예: `GET /api/career/timing`) 호출 → 200 확인.
3. `POST /api/auth/logout` 호출(쿠키 포함).
4. 1번의 `accessToken`으로 같은 보호된 API 재호출 → 401(블랙리스트에 의해 거부) 확인.
5. 로그아웃 응답의 `Set-Cookie`에 `Max-Age=0`(쿠키 삭제 지시) 포함 확인.
6. 일반 보호 API(로그인/갱신/로그아웃이 아닌 경로)를 Refresh Token 쿠키 없이 호출 → Access Token만으로 정상 응답되는지 확인(FR-005).

### US2 — 쿼터 보존(외부 호출 실패 시)

1. FastAPI 또는 궁합 분석 외부 호출이 실패하도록 임시로 엔드포인트를 차단(테스트 환경에서 mock 서버 다운 또는 잘못된 `FASTAPI_URL`).
2. 궁합 분석 요청 전/후 `GET /api/users/me/usage`(또는 해당 쿼터 조회 API) 값을 비교 → 실패 후에도 쿼터가 요청 전과 동일함을 확인.
3. 정상 환경으로 복구 후 성공 요청 1회 → 쿼터가 정확히 1 감소함을 확인.

### US3 — 5xx 재시도

1. 공공데이터 API가 일시적으로 500을 반환하도록 mock 설정(1~2회 실패 후 성공하도록) → 기업 정보 조회 요청이 최종적으로 성공 응답을 받는지 확인(재시도 로그로 확인).
2. mock이 400을 반환하도록 설정 → 즉시(재시도 없이) 오류 응답 확인.

### US4 — CORS 헤더 화이트리스트

1. 브라우저 또는 curl로 `OPTIONS` 사전 요청에 `Access-Control-Request-Headers: X-Custom-Header` 포함 → 허용되지 않음(`Access-Control-Allow-Headers`에 미포함) 확인.
2. `Authorization`, `Content-Type`만 요청 헤더로 사용하는 실제 API 호출은 정상 동작 확인.

### US5 — 동시 생성 안전성 (분산락)

1. `test/concurrency/` 하위 신규 테스트: 동일 `userProfileId`에 대해 스레드 풀로 20~100건 동시 분석 요청 실행.
2. DB에서 `saju_result` 테이블의 해당 `user_profile_id` 행 수가 정확히 1인지 확인(SC-005).
3. 진짜 무결성 위반(예: 존재하지 않는 `user_profile_id` 참조)을 유발하는 케이스는 락 여부와 무관하게 오류로 처리되는지 별도 단위 테스트로 확인.

### US6 — 정본 재사용/소유권 분리

1. 사용자 A, B가 동일한 `birthDate`+`birthTime`으로 각각 분석 요청 → 동일한 `SajuResult.id` 참조 확인, FastAPI/OpenAI 재호출 없음(로그로 확인).
2. 사용자 B가 사용자 A의 상담/피드백 리소스 ID로 접근 시도 → `UserSajuAccess` 매핑이 없으므로 403/404 확인.
3. 정본이 처음 생성되는 요청 전/후 요청자의 쿼터가 변하지 않는지 확인(FR-016).

### US7 — 선택적 출생시간

1. `birthTime` 없이 회원가입 → 가입 성공 확인.
2. 해당 사용자로 분석 요청 → 200 확인, 응답에서 시(時) 기반 세부 필드([contracts/career-quota-and-retry.md](./contracts/career-quota-and-retry.md) 참고)가 없는지 확인.
3. `birthTime` 포함 가입 사용자는 전체 필드를 받는지 대조 확인.

### US8 — 관리자 감사로그

1. 올바른/틀린 관리자 자격증명으로 각각 `POST /admin/login` 호출.
2. 감사로그 저장소(테이블 또는 로그 조회 API)에서 두 시도 모두 기록되어 있는지 확인 — 성공/실패 구분 필드 확인.

### US9 — 구조 일관성 (정적 검증)

1. `career/controller`, `career/dto` 하위에 `CareerTiming`/`Compatibility`/`Consultation`/`Feedback` 컨트롤러·DTO가 위치하는지 확인(디렉터리 리스팅).
2. `grep`으로 `FeedbackType`, `InvalidPasswordException` 등 삭제 대상 식별자가 코드베이스에 더 이상 존재하지 않는지 확인.
3. `AdminAuthenticationService`가 `admin/repository` 하위 리포지토리만 참조하는지 확인.
