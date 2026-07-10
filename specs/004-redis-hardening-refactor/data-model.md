# Phase 1 Data Model: Redis 도입 및 백엔드 전면 하드닝/리팩토링

## 신규/변경 JPA 엔티티 (MySQL)

### `User` (변경)

기존 필드(`id, email, passwordHash, name, role, status, lastLoginAt, termsAgreedAt, privacyAgreedAt, deletedAt, createdAt, updatedAt`)에 추가:

| 필드 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `birthDate` | `LocalDate` | 2단계 롤아웃(아래 참고) | 회원가입 시 필수 입력 (FR-018) |
| `birthTime` | `LocalTime` | `nullable=true` | 회원가입 시 선택 입력 (FR-018) — 값이 없으면 시간 기반 결과 제외 처리(FR-020)의 기준 |

**`birthDate` 2단계 롤아웃**:
1. **Phase 1 (초기)**: `birthDate` 컬럼을 `nullable=true`로 추가 → 신규 가입자만 입력, 기존 행은 `NULL` 유지
2. **Phase 2 (사후)**: 기존 사용자 행에 대한 백필(운영 별도 절차) → `birthDate` 제약을 `nullable=false`로 전환

이유: 기존 사용자 행을 한 번에 NOT NULL로 변경하면 스키마 변경 자체가 실패하거나 긴 lock-time을 야기하므로, 먼저 nullable 컬럼을 추가 후 별도 마이그레이션 과정에서 값을 채우는 방식을 채택.

### `career/entity/SajuResult` (변경 — B1)

변경 전: `user_profile_id`(FK) + `user_id`(FK), 유니크 `(user_id, user_profile_id)`.

변경 후:

| 필드 | 변경 내용 |
|---|---|
| `user` (FK `user_id`) | **제거** — 정본은 특정 사용자에 종속되지 않음 |
| `userProfile` (FK `user_profile_id`) | 유지, **단독 유니크 제약**(`uk_saju_result_user_profile` → `user_profile_id` 단일 컬럼)으로 변경 |
| 나머지(`fetchedAt`, 자식 엔티티 연관) | 변경 없음 |

**마이그레이션 순서(운영 DB, Git 미포함 별도 스크립트)**:
1. `user_profile_id` 기준으로 중복된 `saju_result` 행 그룹 조회, 그룹별로 "살아남을 정본"(예: 가장 오래된 `fetched_at` 또는 최소 `id`) 1건 선정.
2. 그룹 내 살아남을 정본을 참조하도록 하위 데이터(`career_consultation.saju_result_id`, `career_fortune.saju_result_id`, `feedback` 등 `saju_result_id`를 FK로 갖는 모든 테이블)를 `UPDATE`.
3. 각 사용자-정본 접근 이력을 `UserSajuAccess`에 `INSERT`(아래 신규 엔티티) — 삭제될 중복 정본에 연결되어 있던 사용자도 살아남은 정본에 대한 접근 권한을 갖도록 매핑 생성.
4. 중복 정본 행 삭제, `user_id` 컬럼/FK/기존 복합 유니크 제약 삭제, `user_profile_id` 단독 유니크 제약 생성.

### `career/entity/UserSajuAccess` (신규 — B1)

| 필드 | 타입 | 제약 |
|---|---|---|
| `id` | `Long` | PK, IDENTITY |
| `user` | `User` (`@ManyToOne`, FK `user_id`, `fetch=LAZY`) | not null |
| `sajuResult` | `SajuResult` (`@ManyToOne`, FK `saju_result_id`, `fetch=LAZY`) | not null |
| `createdAt` | `LocalDateTime` | `@CreatedDate`, not null |

제약: 유니크 `(user_id, saju_result_id)`(동일 사용자-정본 매핑 중복 방지), 인덱스 `user_id`(소유권 조회 최적화), 인덱스 `saju_result_id`(정본별 접근자 목록 조회).

**용도**: `FeedbackService` 등 기존에 `SajuResult.user_id`로 하던 소유권 검증을 `UserSajuAccessRepository.existsByUserIdAndSajuResultId(userId, sajuResultId)`로 대체. 정본을 최초 생성/최초 접근할 때 이 매핑 행을 함께 생성(단, 매핑 생성 자체는 쿼터 차감과 무관 — FR-016).

### `career/enums/AnalysisType` (변경 — B2)

기존: `SAJU, CAREER_CONSULTATION, COMPANY_COMPATIBILITY`. 변경 없음(값 유지). `career/enums/FeedbackType`(`CAREER_TIMING, CONSULTATION, COMPATIBILITY`) 삭제, 참조 전량을 아래 매핑으로 치환:

| 삭제되는 `FeedbackType` 값 | 대체되는 `AnalysisType` 값 |
|---|---|
| `CAREER_TIMING` | `SAJU` |
| `CONSULTATION` | `CAREER_CONSULTATION` |
| `COMPATIBILITY` | `COMPANY_COMPATIBILITY` |

영향 파일(연구 단계 확인): `UserSatisfactionFeedback`(엔티티 필드 타입), `admin/controller/AdminFeedbackController`, `admin/service/AdminFeedbackService`, `admin/dto/FeedbackListDTO`, `admin/dto/FeedbackStatDTO`, `admin/repository/AdminFeedbackQueryRepository`, `dto/request/SatisfactionFeedbackRequest`.

### 예외 클래스 (D1, 데이터 모델 관점에서는 계층 변경만)

- `FeedbackNotAllowedException` → `SajuException`(또는 해당 루트 예외) 하위로 이동, 기존 필드/메시지 유지.
- `TokenHashException` → 동일 계층 편입 + `SajuGlobalExceptionHandler`에 전용 `@ExceptionHandler` 추가.

## Redis 데이터 구조 (비-JPA)

### Refresh Token 세션

| Key | Value | TTL |
|---|---|---|
| `refresh-token:{jti}` | JSON `{ "userId": <Long>, "tokenHash": "<sha256>" }` | `jwt.refresh-token-expiration`(ms) |

연산: 로그인/갱신 성공 시 `SET` (TTL 포함), 갱신 시 기존 키 `DEL` 후 신규 `SET`(회전), 로그아웃/탈퇴 시 `DEL`.

### Access Token 블랙리스트

| Key | Value | TTL |
|---|---|---|
| `access-blacklist:{jti}` | `"1"`(존재 여부만 의미) | Access Token의 **남은 유효시간**(발급 시 `exp` 기준 계산) |

연산: 로그아웃/탈퇴 시 `SET ... EX <remainingSeconds>`, 매 요청 인증 필터에서 `EXISTS` 확인.

### 분산락 키(Redisson `RLock`, 값은 Redisson이 내부 관리하므로 스키마 없음)

| Key 패턴 | 보호 대상 |
|---|---|
| `lock:saju-result:{userProfileId}` | `SajuResultProvider.findOrCreate` |
| `lock:user-profile:{birthDate}:{birthTime}` | `UserProfileProvider.findOrCreate` |
| `lock:career-consultation:{sajuResultId}:{yearMonth}` | `ConsultationSaveService.insertOrRecoverOnConflict` |
| `lock:company-compatibility:{userProfileId}:{companyName}:{targetRoleCategory}` | `CompanyMatchingService.analyzeCompatibility` |

## 삭제 대상 (F, 죽은 코드)

- `entity/RefreshToken.java`, `repository/RefreshTokenRepository.java`, 대응 테이블 `refresh_token`(Redis 이전 검증 완료 후 별도 DDL로 DROP — Git 커밋 대상 마이그레이션 스크립트는 스키마 DDL이므로 데이터 마이그레이션과 달리 커밋 포함 가능, 단 실행은 운영 배포 절차에 따름).
- `career/enums/FeedbackType.java`.
- `SajuResultJdbcRepository`의 T049 TODO 주석 블록(B1 재설계로 논의 자체가 해소됨).
