# Implementation Plan: Redis 도입 및 백엔드 전면 하드닝/리팩토링

**Branch**: `004-redis-hardening-refactor` | **Date**: 2026-07-09 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/004-redis-hardening-refactor/spec.md`

## Summary

SSAju 백엔드(Spring Boot 4.0.5 / Java 21)에 Redis 인프라(Refresh Token TTL 저장, Access Token 블랙리스트, Redisson 분산락)를 도입하고, 이를 기반으로 세션 보안 결함(A2, 필터 범위), API 쿼터 증발 버그(quota), 외부 제공자 재시도 정책(A1), CORS 헤더(A3), 사주 정본 캐싱/소유권 재설계(B1/B2), 패키지·예외·검증·죽은 코드·감사로그·쿼리 계층 일관성(C~H)을 순차적으로 해결한다. 기술적 접근은 기존 계층형 아키텍처(Controller→Service→Repository)와 RestClient+`@Retryable` 패턴을 그대로 유지하면서, 동시성 제어 수단만 "DB 예외 기반 재시도"에서 "Redisson 분산락 선점"으로 교체하고, 세션 상태 저장소를 "RDBMS 영구 저장"에서 "Redis TTL 저장"으로 교체한다.

## Technical Context

**Language/Version**: Java 21 (toolchain), Gradle 9.4.1, Spring Boot 4.0.5

**Primary Dependencies**:
- 기존: Spring Web MVC, Spring Data JPA, Spring Validation, Spring Security, Spring AI(OpenAI, `2.0.0-M4`), `spring-retry` + `spring-aspects`(`@EnableRetry`, `spring-boot-starter-aop` 미사용), `jjwt` 0.12.6, `springdoc-openapi` 3.0.3, Thymeleaf(관리자 SSR)
- 신규: `spring-boot-starter-data-redis`(Lettuce, Refresh Token/블랙리스트 저장), `org.redisson:redisson-spring-boot-starter`(분산락 `RLock`)

**Storage**: MySQL 8(JPA/Hibernate, 기존 도메인 데이터) + Redis(신규: Refresh Token TTL 저장, Access Token 블랙리스트, 분산락 키) — 스키마 마이그레이션 도구(Flyway/Liquibase) 없음, `ddl-auto=validate`(운영)/`update`(로컬)이며 스키마 변경은 수동 DDL로 관리되는 기존 관행을 유지

**Testing**: JUnit 5 + AssertJ(Given-When-Then), Spring Boot Test, `spring-security-test`, H2(단위), MySQL Testcontainers(통합 — 이미 `build.gradle`에 존재, INSERT IGNORE 등 MySQL 전용 문법 검증 목적), 신규: Redis Testcontainers(분산락/TTL 만료 통합 테스트), `test/concurrency/` 패키지(동시 요청 재현 테스트)

**Target Platform**: Linux 서버(Docker), JSON REST API(사용자) + Thymeleaf SSR(관리자 대시보드) 병행 서비스

**Project Type**: 단일 Spring Boot 백엔드 웹 서비스(web-service) — 프론트엔드는 별도 리포지토리, 본 계획 범위 밖

**Performance Goals**: 기존 외부 API 타임아웃 예산 유지(FastAPI 3s/OpenAI 8s/공공데이터 5s) — 분산락 대기시간(`tryLock waitTime`)은 이 예산을 잠식하지 않도록 짧게(예: DB 트랜잭션 수준인 수백 ms~2s) 설정. Redis 왕복 지연이 기존 요청 P95 응답시간에 실질적 영향을 주지 않아야 함(SC-004~SC-006 참고)

**Constraints**:
- 외부 I/O(FastAPI/OpenAI/공공데이터)를 호출하는 Service 메서드는 `@Transactional` 금지 원칙 유지(`skills/code-style-guide.md` 트랜잭션 절)
- `career/` 패키지는 `entity/enums/domain/converter/mapper/provider/util/validator/caller`만 두고 `controller/service/repository`는 최상위에 둔다는 기존 관례 — 이번 C1 작업으로 **career 관련 컨트롤러/DTO만** `career/controller`, `career/dto` 하위로 이동(career 도메인 서비스/레포지토리는 최상위 유지 범위 밖, 컨트롤러·DTO 이동만 지시사항에 명시됨)
- `skills/architecture-guide.md`의 "Phase 1 제약사항: Redis/전역 캐시 사용 금지"는 이번 하드닝 이니셔티브(이해관계자 명시적 결정)로 **명시적으로 대체(supersede)** — Complexity Tracking에 근거 기록
- 기존 `RefreshToken` JPA 엔티티/테이블/리포지토리는 Redis 이전 완료 후 죽은 코드가 되므로 F(죽은 코드 정리) 단계에서 제거 대상

**Scale/Scope**: 인증/세션(전역 영향), career 도메인(사주/컨설팅/궁합/피드백), admin 도메인(감사로그) 전반에 걸친 하드닝 — 스펙 상 9개 User Story, FR-001~FR-033

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

`.specify/memory/constitution.md`는 아직 프로젝트 고유 원칙이 채워지지 않은 템플릿 상태다. 이 프로젝트는 대신 `CLAUDE.md` + `skills/architecture-guide.md` + `skills/code-style-guide.md` + `skills/git-workflow.md`를 사실상의 헌법(운영 규칙)으로 사용하고 있으므로, 이 문서들에서 도출한 게이트로 대체 평가한다.

| Gate | 판정 | 근거 |
|---|---|---|
| 계층 분리(Controller 얇게/Service에 로직 집중/Repository는 DB만) | PASS | C3(AuthController 로직 이관), C4(admin 리포지토리 경계) 항목이 이 원칙을 강화하는 방향이며 위반 없음 |
| 외부 I/O 메서드에 `@Transactional` 금지 | PASS | `CompanyMatchingService.analyzeCompatibility`, `AuthService.login` 등은 현재도 미적용 상태 유지, 쿼터 차감 순서 변경도 이 원칙과 상충하지 않음(§B: 성공 후 차감은 로컬 DB 연산이므로 트랜잭션 경계는 Repository/짧은 서비스 메서드 단위로 유지) |
| 상수: 도메인=Enum, 기술=Static Class, 매직넘버/문자열 금지 | PASS | 신규 Redis 키 패턴, TTL 값, 분산락 lease/wait time은 static 상수 클래스(`RedisKeyConstants`, `LockConstants` 등)로 관리 예정 |
| DTO는 record | PASS | 신규/수정 DTO(가입 시 birthTime 추가, 쿼터 조정 검증 등) 모두 record 유지 |
| Lombok 제약(@Getter/@NoArgsConstructor(PROTECTED)/@Builder만, equals/hashCode ID 수기) | PASS | 신규 엔티티(`UserSajuAccess`)도 동일 규칙 적용 |
| 모든 연관관계 `fetch = LAZY` 명시 | PASS | `UserSajuAccess`의 `User`/`SajuResult` 연관관계에 명시 예정 |
| 예외는 커스텀 예외 계층 + 전역 핸들러, try-catch로 삼키지 않음 | PASS | D1(예외 계층 편입), A1(5xx 원본 예외 유지) 모두 이 원칙 강화 |
| 로깅 보안(PII/토큰/API Key 로그 금지) | PASS | Redis 저장 토큰 해시/블랙리스트 키는 로그에 원문 미기록(사용자 ID/토큰 ID만) |
| `skills/architecture-guide.md` "Phase 1: Redis/전역 캐시 금지" | **명시적 대체(Superseded)** | 이해관계자가 이번 이니셔티브에서 명시적으로 Redis 도입을 결정 — 아래 Complexity Tracking에 기록 |

## Project Structure

### Documentation (this feature)

```text
specs/004-redis-hardening-refactor/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md         # Phase 1 output
├── quickstart.md         # Phase 1 output
├── contracts/            # Phase 1 output
│   ├── auth-token-endpoints.md
│   ├── career-quota-and-retry.md
│   └── admin-audit-log.md
└── tasks.md              # Phase 2 output (/speckit-tasks, 본 명령 범위 밖)
```

### Source Code (repository root)

기존 `SSAju/src/main/java/ssafy/SSAju/` 구조(단일 Gradle 프로젝트, `web-service`)를 그대로 유지하며 다음 위치에 신규/변경 파일이 추가된다:

```text
SSAju/src/main/java/ssafy/SSAju/
├── config/
│   ├── RedisConfig.java              # [신규] Lettuce ConnectionFactory, RedisTemplate
│   ├── RedissonConfig.java           # [신규] RedissonClient 빈
│   ├── AuditingConfig.java           # [수정] 커스텀 DateTimeProvider 등록 (Clock 연결)
│   ├── ClockConfig.java              # [기존, 재사용] Clock 빈
│   ├── SecurityConfig.java           # [수정] CORS allowedHeaders 화이트리스트, JWT 필터 체인
│   ├── AsyncConfig.java              # [수정] AsyncUncaughtExceptionHandler 등록
│   └── WebMvcConfig.java             # [삭제 대상] 빈 클래스 (C6)
├── security/
│   ├── redis/
│   │   ├── RefreshTokenRedisRepository.java   # [신규] Redis 기반 Refresh Token 저장/조회/삭제
│   │   └── AccessTokenBlacklistService.java   # [신규] Redis 기반 블랙리스트 등록/조회
│   ├── AbstractJwtValidationFilter.java       # [신규] 사용자/관리자 JWT 필터 공통 로직 (C5)
│   └── (기존 JwtAuthenticationEntryPoint/JwtAccessDeniedHandler/JwtExceptionFilter 유지)
├── util/ 또는 annotation+aspect/
│   └── DistributedLock 어노테이션 + AOP 헬퍼        # [신규] Redisson RLock 공통 래퍼 (Redis 항목 #2, C2)
├── filter/
│   └── TokenValidationFilter.java     # [수정] 쿠키 기반 + 갱신/로그아웃 경로만 검사하도록 축소
├── controller/
│   └── AuthController.java            # [수정] 쿠키 세팅/삭제, logout·getCurrentUserId 로직은 Service로 이관 (C3)
├── career/
│   ├── controller/                    # [신규 위치, C1] CareerTimingController, CompatibilityController, ConsultationController, FeedbackController 이동
│   ├── dto/                           # [신규 위치, C1] 각 컨트롤러의 request/response record 이동
│   ├── entity/
│   │   ├── SajuResult.java            # [수정] user FK/유니크 제거, user_profile_id 단독 유니크 (B1)
│   │   └── UserSajuAccess.java        # [신규] User↔SajuResult 접근 매핑 (B1)
│   ├── repository/UserSajuAccessRepository.java  # [신규]
│   ├── enums/AnalysisType.java        # [수정] FeedbackType 흡수, 값 통합 (B2)
│   └── provider/SajuResultProvider.java # [수정] 분산락 적용, insertOrIgnore 경로 제거 (Redis 항목 #2)
├── service/
│   ├── CareerFortuneService.java      # [수정] Quota 차감 로직 제거 (B1)
│   ├── CompanyInfoService.java        # [수정] 5xx 원본 예외 유지 (A1)
│   ├── CompanyMatchingService.java    # [수정] 성공 후 쿼터 차감 + 분산락 (Redis 항목 #3, C2)
│   ├── DailyApiUsageService.java      # [수정] 차감 시점/보상 트랜잭션
│   ├── AuthService.java               # [수정] Clock 사용, Redis 세션 연동, signup birthTime 처리
│   ├── FeedbackService.java           # [수정] 소유권 검증을 UserSajuAccess 참조로 변경 (B1)
│   └── ConsultationSaveService.java   # [수정] 분산락 적용 후 DataIntegrityViolationException 분기 단순화 (C2)
├── exception/
│   ├── FeedbackNotAllowedException.java  # [수정] SajuException 계층 편입 (D1)
│   └── TokenHashException.java           # [수정] SajuException 계층 편입 + 전용 핸들러 (D1)
├── admin/
│   └── service/AdminAuthenticationService.java  # [수정] admin 전용 리포지토리 사용 + 감사로그 (C4, G1)
└── dto/request/
    ├── SignupRequest.java             # [수정] birthDate(필수)/birthTime(선택) 추가 (#5)
    ├── UsageAdjustmentRequestDTO.java # [수정] @NotNull 추가 (E1)
    └── CompatibilityRequest.java      # [수정, career/dto로 이동] 시간 필드 @NotNull (E2, C1)

SSAju/src/test/java/ssafy/SSAju/
├── concurrency/                        # [확장] 분산락 동시 요청 재현 테스트
└── integration/                        # [확장] Redis + MySQL Testcontainers 통합 테스트
```

**Structure Decision**: 별도 서비스/모듈 분리 없이 기존 단일 Spring Boot 프로젝트 구조를 유지한다. `career/` 패키지는 기존 관례대로 `controller`/`dto`가 없었으나 이번에 C1에 따라 추가되며, 나머지(entity/enums/provider 등)는 기존 위치를 유지한 채 내부 로직만 수정한다. Redis 관련 신규 컴포넌트는 인증에 밀접하므로 `security/redis/` 하위에, 분산락 공통 헬퍼는 기존 `annotation/`+`aspect/` 컨벤션(`@AuditLog`+`AuditLoggingAspect` 패턴과 동일)을 따라 어노테이션+AOP 방식으로 추가한다.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| `skills/architecture-guide.md` "Phase 1 제약사항: Redis/전역 캐시 사용 금지" 대체 | Refresh Token 무한 누적, Access Token 즉시 무효화 불가, DB 예외 기반 동시성 재시도의 비일관성이라는 실제 운영 결함을 해결하려면 TTL 기반 외부 저장소(Redis)와 분산락이 필요 — 이해관계자가 이번 이니셔티브에서 이를 명시적으로 결정함 | RDBMS 컬럼(`revoked_at`)만으로 Access Token 즉시 무효화를 구현하면 매 요청마다 DB 조회가 필요해 지연이 커지고, 배치 삭제 스케줄러를 추가해도 "즉시 무효화"라는 요구(SC-001)를 만족시키기 어려움. DB 유니크 제약 기반 재시도는 이미 A~E 항목에서 지적된 4가지 서로 다른 구현으로 분기되어 있어 통일된 해법(분산락)이 더 단순함 |

## Phase 0 & Phase 1 Outputs

Phase 0: [research.md](./research.md)
Phase 1: [data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

## Post-Design Constitution Re-check

Phase 1 설계(데이터 모델, 컨트랙트) 반영 후 재평가 — 신규 엔티티(`UserSajuAccess`)와 Redis 저장 스키마 모두 위 게이트를 그대로 통과하며, 신규 위반 없음. `career/controller`, `career/dto` 이동(C1)은 기존 `career/` 패키지 명명 규칙과 충돌하지 않음(엔티티/enum/provider 등 기존 하위 패키지 네이밍 패턴과 동일한 방식으로 추가).
