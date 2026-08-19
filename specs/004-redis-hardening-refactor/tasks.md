---

description: "Task list for Redis 도입 및 백엔드 전면 하드닝/리팩토링"

---

# Tasks: Redis 도입 및 백엔드 전면 하드닝/리팩토링

**Input**: Design documents from `specs/004-redis-hardening-refactor/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: 포함됨 — 프로젝트 규칙(`CLAUDE.md`: "Test-before-commit is mandatory")과 사용자 지시("각 항목이 끝날 때마다 `./gradlew test` 실행")에 따라 각 User Story마다 검증 테스트 태스크를 포함한다.

**Organization**: 태스크는 User Story별로 그룹화되어 있으며, 사용자가 지정한 진행 순서인 **[Redis 인프라 및 신규 아키텍처 세팅] → A → B → C/D/E → F → G → H**를 Setup/Foundational → US1~US9 순서에 그대로 반영했다. 각 User Story 완료 후 `cd SSAju && ./gradlew test`로 `BUILD SUCCESSFUL`을 확인한 뒤 다음 단계로 넘어간다.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 병렬 실행 가능(다른 파일, 선행 의존성 없음)
- **[Story]**: 이 태스크가 속한 User Story(US1~US9)
- 파일 경로는 모두 `SSAju/src/main/java/ssafy/SSAju/` 또는 `SSAju/src/test/java/ssafy/SSAju/` 기준 상대경로로 표기

## Path Conventions

단일 Gradle 프로젝트(`SSAju/`), 소스는 `SSAju/src/main/java/ssafy/SSAju/`, 테스트는 `SSAju/src/test/java/ssafy/SSAju/`. `plan.md`의 Project Structure 섹션과 동일.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Redis/Redisson 의존성 및 설정값 도입 (사용자 지시 "[Redis 인프라 및 신규 아키텍처 세팅]" 1단계)

- [x] T001 `SSAju/build.gradle`에 `implementation 'org.springframework.boot:spring-boot-starter-data-redis'`, `implementation 'org.redisson:redisson-spring-boot-starter:3.25.1'` 추가 — *Boot 4.0.5 호환을 위해 `redisson-spring-boot-starter:4.0.0`으로 상향 적용*
- [x] T002 [P] `SSAju/build.gradle`의 `testImplementation`에 Redis Testcontainers 의존성 추가(`org.testcontainers:testcontainers` 하위 Redis 모듈 또는 GenericContainer용 core 모듈 — 기존 MySQL Testcontainers와 동일 버전 라인(`1.20.4`) 사용)
- [x] T003 [P] `SSAju/src/main/resources/application.yaml`에 `spring.data.redis.host/port`(env `${REDIS_HOST:localhost}`/`${REDIS_PORT:6379}`), `redisson.lock.wait-time-ms`/`redisson.lock.lease-time-ms` 등 신규 설정 키 추가(매직넘버 금지 원칙에 따라 상수 클래스에서 읽도록 `@ConfigurationProperties`로 매핑할 위치도 함께 준비)

**Checkpoint**: `./gradlew build` 성공(신규 의존성 다운로드 확인) — 아직 신규 기능 코드는 없으므로 `./gradlew test`는 기존과 동일하게 통과해야 함

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 모든 User Story가 공유하는 Redis 연결, 분산락 공통 유틸, 결정론적 Clock/Auditing 배선 — "[Redis 인프라 및 신규 아키텍처 세팅]" 완료 단계

**⚠️ CRITICAL**: 이 phase가 끝나기 전에는 어떤 User Story 작업도 시작할 수 없음

- [x] T004 `config/RedisConfig.java` 생성 — Lettuce `RedisConnectionFactory` + `StringRedisTemplate`(또는 `RedisTemplate<String,String>`) 빈 등록
- [x] T005 [P] `config/RedissonConfig.java` 생성 — `application.yaml`의 host/port를 읽어 `RedissonClient` 빈 등록 — *`redisson-spring-boot-starter:4.0.0`의 `RedissonAutoConfigurationV4`(`@ConditionalOnMissingBean`)가 `spring.data.redis.*`로 `RedissonClient`를 이미 자동 구성함을 바이트코드로 확인 → 별도 `RedissonConfig.java`는 생성하지 않고 자동 구성에 위임(`DistributedLockAspect`에서 자동 구성된 빈 사용으로 검증 완료)*
- [x] T006 [P] `util/RedisKeyConstants.java`(또는 `career/enums`/`util` 컨벤션에 맞는 위치) 생성 — `refresh-token:`, `access-blacklist:`, `lock:saju-result:`, `lock:user-profile:`, `lock:career-consultation:`, `lock:company-compatibility:` 등 키 prefix를 상수화(`data-model.md` Redis 스키마 표 반영)
- [x] T007 `annotation/DistributedLock.java`(락 키 SpEL 표현식 속성 포함) + `aspect/DistributedLockAspect.java` 생성 — Redisson `RLock.tryLock(waitTime, leaseTime, TimeUnit)` 래핑, 락 획득 실패 시 전용 예외(`exception/` 하위 신규 또는 기존 계층 재사용) 발생, 성공 시 메서드 실행 후 `unlock()` 보장(try-finally)
- [x] T008 `config/JpaAuditingConfig.java`에 커스텀 `DateTimeProvider` 빈 등록 — 기존 `config/ClockConfig.java`의 `Clock` 빈을 주입받아 `@CreatedDate`/`@LastModifiedDate`가 시스템 시간이 아닌 주입된 `Clock` 기준으로 기록되도록 연결(이해관계자 결정 #4)
- [x] T009 [P] `SSAju/src/test/java/ssafy/SSAju/aspect/DistributedLockAspectTest.java` 작성 — 락 획득/해제, `tryLock` 타임아웃 시 예외 발생을 검증(Redis Testcontainers 사용)
- [x] T010 [P] `SSAju/src/test/java/ssafy/SSAju/config/JpaAuditingConfigTest.java` 작성 — 테스트용 고정 `Clock`을 주입했을 때 엔티티의 `createdAt`이 시스템 시간이 아닌 그 `Clock` 값과 일치하는지 검증

**Checkpoint**: ✅ 완료 (`./gradlew clean test` BUILD SUCCESSFUL, `feat/redis-foundational-infra` 브랜치) — 인프라 준비 완료. 이후 User Story는 우선순위 순서(P1→P4)대로 진행하되, 서로 다른 담당자가 있다면 병렬 진행 가능

---

## Phase 3: User Story 1 - 로그아웃/토큰 만료 후 재사용 불가 (세션 보안 하드닝) (Priority: P1) 🎯 MVP

**Goal**: Refresh Token을 Redis TTL 저장으로 전환, Access Token은 로그아웃/탈퇴 시 Redis 블랙리스트로 즉시 무효화, Refresh Token은 쿠키로만 전달하고 갱신/로그아웃 경로에서만 검사

**Independent Test**: 로그인 후 로그아웃 → 이전 Access Token으로 보호 API 호출 시 401, 만료된 Refresh Token으로 갱신 시도 시 거부 및 Redis에 잔존 키 없음, 일반 보호 API는 Refresh Token 쿠키 없이도 정상 동작 (quickstart.md "US1" 시나리오)

### Tests for User Story 1

- [x] T011 [P] [US1] 통합 테스트 `SSAju/src/test/java/ssafy/SSAju/integration/AuthTokenSecurityIntegrationTest.java` — 로그인→보호 API 성공→로그아웃→동일 Access Token으로 보호 API 재호출 시 401 확인
- [x] T012 [P] [US1] 단위/통합 테스트 `SSAju/src/test/java/ssafy/SSAju/security/redis/RefreshTokenRedisRepositoryTest.java` — TTL 만료 후 자동 조회 불가(수동 삭제 없이) 확인(Redis Testcontainers)
- [x] T013 [P] [US1] 필터 테스트 `SSAju/src/test/java/ssafy/SSAju/filter/TokenValidationFilterTest.java` — `/api/auth/refresh`, `/api/auth/logout` 이외 경로는 Refresh Token 쿠키 없이도 필터를 통과하는지 확인

### Implementation for User Story 1

- [x] T014 [US1] `security/redis/RefreshTokenRedisRepository.java` 생성 — `save(jti, userId, tokenHash, ttl)`, `find(jti)`, `delete(jti)` (T004, T006 의존)
- [x] T015 [US1] `security/redis/AccessTokenBlacklistService.java` 생성 — `blacklist(jti, tokenExpiry)`, `isBlacklisted(jti)` (T004, T006 의존). 남은 유효시간은 시스템 시간이 아닌 주입된 `Clock`(T008/`config/ClockConfig.java`) 기준으로 계산하고, 클럭 스큐(앱 서버-Redis 서버 간 시간 오차) 및 계산~저장 사이 지연을 방어하기 위해 계산된 TTL에 고정 패딩(상수화된 최소 60초)을 더해 `SETEX`하도록 구현
- [x] T016 [US1] `security/AbstractJwtValidationFilter.java` 생성 — 사용자/관리자 필터 공통 로직(토큰 파싱→서명/만료 검증→블랙리스트 확인→SecurityContext 설정) 템플릿 메서드로 추출(C5); `filter/JwtAuthenticationFilter.java`, `admin/config/AdminCookieJwtFilter.java`가 이를 상속하도록 리팩토링(T015 의존)
- [x] T017 [US1] `filter/TokenValidationFilter.java` 수정 — `Refresh-Token` 헤더 대신 `Cookie`에서 읽고, `shouldNotFilter`를 반전하여 `/api/auth/refresh`·`/api/auth/logout` **에서만** 검사하도록 축소(이해관계자 결정 #2)
- [x] T018 [US1] `service/AuthService.java` 수정 — `login`/`refreshAccessToken`/`logout`/`deleteUser`가 `entity/RefreshToken`/`repository/RefreshTokenRepository` 대신 T014/T015를 사용하도록 교체, `Instant.now()` 호출부를 주입된 `Clock`으로 교체(C7)
- [x] T019 [US1] `controller/AuthController.java` 수정 — `login`/`refresh` 응답에서 Refresh Token을 `HttpOnly; Secure; Path=/api/auth; SameSite=Strict` 쿠키로만 내려보내고 응답 바디에서 제거, `logout` 시 `Set-Cookie ...Max-Age=0`로 명시적 삭제; `logout`/`getCurrentUserId`의 세부 로직을 `AuthService`로 이관(C3, 컨트롤러는 위임만)
- [x] T020 [US1] `dto/response/AuthTokenResponse.java`/`AuthTokenPair.java` 수정 — `refreshToken` 필드 제거 및 관련 매핑 코드 정리
- [x] T021 [US1] `config/SecurityConfig.java` 수정 — T016에서 생성/변경된 필터 빈 등록 순서 확인 및 조정

**Checkpoint**: ✅ 완료 (PR #40 `feat/redis-session-token-security`, main 병합됨: `9c97f34`/`f93de67`/`09970ba`/`ae6d913`) — `./gradlew test` BUILD SUCCESSFUL 확인됨. User Story 1은 독립적으로 완전히 동작함

---

## Phase 4: User Story 2 - API 외부 호출 실패 시 사용자 쿼터 보존 (Priority: P1)

**Goal**: 궁합 분석(FastAPI/공공데이터) 및 커리어 컨설팅(OpenAI) 등에서 외부 호출 실패 시 일일 쿼터가 소진된 채 남지 않도록 차감 시점/보상 로직 수정

**Independent Test**: 외부 분석 호출을 실패하도록 만든 뒤 쿼터가 요청 전과 동일한지 확인, 성공 시 정확히 1 차감 확인 (quickstart.md "US2")

### Tests for User Story 2

- [x] T022 [P] [US2] 통합 테스트 `SSAju/src/test/java/ssafy/SSAju/integration/DailyQuotaIntegrityIntegrationTest.java` — 외부 호출 실패 시 쿼터 불변, 성공 시 1 차감 확인
- [x] T022-1 [P] [US2] *(계획 변경: 별도 파일 대신 기존 `SSAju/src/test/java/ssafy/SSAju/service/ConsultationServiceTest.java`의 OpenAI 실패 테스트 3종 + 캐시 히트/성공 테스트에 `restoreDailyUsage` 호출/미호출 검증을 추가하는 방식으로 커버 — 동일 목업 셋업 중복 방지)* `ConsultationService.getCareerConsultation`에서 캐시 미스 후 OpenAI 호출이 실패하면 쿼터가 요청 전과 동일하게 복원되는지, 캐시 히트 시에는 애초에 차감되지 않는지, 성공 시 정확히 1 차감되는지 확인
- [x] T022-2 [P] [US2] 단위 테스트 `SSAju/src/test/java/ssafy/SSAju/career/caller/ConsultationOpenAICallerRetryTest.java` + `ConsultationOpenAICallerServerErrorInvestigationTest.java` — 실측 결과 **두 가지 버그를 확인**: (1) Spring AI 자체 내부 재시도(기본 max-attempts=10)가 `@Retryable`(maxAttempts=3)과 이중으로 겹쳐 연결 실패 시 최악 약 75분 소요, (2) Spring AI `ResponseErrorHandler`가 5xx를 원본 `HttpServerErrorException`이 아니라 자체 `TransientAiException`으로 먼저 변환해버려서 기존 catch-all(`재시도 불가`)로 떨어짐 — 운영 로그(2026-07-12 `statusCode=0`/"재시도 불가")와 정확히 일치. 4xx는 `NonTransientAiException`으로 변환됨도 함께 확인

### Implementation for User Story 2

- [x] T023 [US2] `service/DailyApiUsageService.java` 수정 — 쿼터 복원(보상) 메서드 `restoreDailyUsage(userId)` 추가(보상 트랜잭션 방식 채택)
- [x] T024 [US2] `service/CompanyMatchingService.java` `analyzeCompatibility` 수정 — 외부 호출(FastAPI/공공데이터) 실패 시 T023의 `restoreDailyUsage`를 호출하도록 try/catch로 감싸기(사주 계산 구간을 `calculateSajuData` private 메서드로 분리해 try 범위를 명확히 함). US5(T035) 분산락 적용 시 이중 차감 방지를 위한 재조정 필요는 그대로 유효
- [x] T024-1 [US2] `service/ConsultationService.java` `getCareerConsultation` 수정 — `checkAndIncrementDailyUsage`~`openAICaller.call` 구간을 try/catch로 감싸 실패 시 `restoreDailyUsage` 호출
- [x] T024-2 [P] [US2] *(계획보다 범위 확장: retryFor/noRetryFor 조정뿐 아니라 이중 재시도 자체를 제거)* `caller/ConsultationOpenAICaller.java` 수정 — `retryFor`/`catch`를 `TransientAiException`(재시도)/`NonTransientAiException`(비재시도)으로 교체(기존 `HttpServerErrorException`/`HttpStatusCodeException` 분기는 실측 결과 죽은 코드였음), `@Recover`도 교체. `application.yaml`(운영+테스트) 양쪽에 `spring.ai.retry.max-attempts: 0` 추가해 Spring AI 자체 재시도를 끄고 재시도 주체를 `@Retryable` 하나로 일원화. 신규 단위 테스트 `ConsultationOpenAICallerTest.java` 추가, 조사 테스트 2종을 회귀 테스트로 전환(연결 실패 30초 이내 실패, 500 응답 정확히 3회 재시도 검증)

**Checkpoint**: `./gradlew test` 통과 — US1+US2 모두 독립적으로 동작

---

## Phase 5: User Story 3 - 외부 데이터 제공자 일시 장애에 대한 자동 재시도 (Priority: P2)

**Goal**: `CompanyInfoService`의 5xx를 `SajuDataService`와 동일하게 원본 예외로 유지해 `@Retryable`이 작동하도록 수정(A1)

**Independent Test**: 5xx mock 시 재시도 후 성공 응답, 4xx mock 시 즉시 비재시도 오류 (quickstart.md "US3")

### Tests for User Story 3

- [x] T025 [P] [US3] 단위 테스트 `SSAju/src/test/java/ssafy/SSAju/service/CompanyInfoServiceRetryTest.java` — 5xx 응답 시 원본 예외(`HttpServerErrorException`)가 그대로 전파되어 `@Retryable`이 재시도하는지, 4xx는 즉시 `Optional.empty()`로 처리되는지 확인(기존 `SajuDataServiceTest` 패턴 참고). *(계획 문구 정정: 4xx는 "커스텀 예외 변환"이 아니라 기존부터 `Optional.empty()` 반환이었음 — 이 부분은 변경 범위 밖이라 그대로 유지)*. 기존 `CompanyInfoServiceTest.shouldThrowException_WhenServerError`도 새 기대값(원본 예외 재전파)으로 갱신
- [x] T025 검증: 수정 전 두 테스트 모두 `AssertionError`로 실패 확인(구현 전 실패 필수 확인 완료) → T026 적용 후 전체 통과
- [x] T025 보강(코드 리뷰 반영): `CompanyInfoServiceRetryTest`는 `new CompanyInfoService(...)`로 직접 생성한 인스턴스를 호출해 `@Retryable`/`@Recover` AOP 프록시를 거치지 않으므로(원본 예외 재전파 1회 호출까지만 검증 가능) 신규 `SSAju/src/test/java/ssafy/SSAju/service/CompanyInfoServiceRetryProxyTest.java`(`@SpringBootTest` + `@MockitoBean(name="publicDataRestClient")`)를 추가해 실제 프록시 빈으로 (1) 5xx 1회 실패 후 재시도 성공 시 호출 횟수 2회, (2) 5xx 2회 연속 실패 시 `ExternalApiException`으로 복구, (3) `ResourceAccessException` 2회 연속 실패 시 `ExternalApiException`으로 복구, (4) 4xx는 호출 횟수 1회(재시도 없음)를 검증. 기존 `CompanyInfoServiceRetryTest`(순수 단위 테스트, 직접 생성)는 그대로 유지 — 목적이 다름(비즈니스 분기 검증 vs AOP 동작 검증)

### Implementation for User Story 3

- [x] T026 [US3] `service/CompanyInfoService.java` 수정 — 5xx 분기에서 `PublicDataApiException` 변환 로직 제거, 원본 예외 그대로 rethrow; `@Retryable(retryFor = {...})`에 `HttpServerErrorException` 추가(4xx는 기존 `Optional.empty()` 로직 유지). **트랜잭션 전파 방어**: `CompanyMatchingService.analyzeCompatibility`(호출 체인 상위)에 `@Transactional`이 없음을 소스 확인(외부 I/O 메서드 `@Transactional` 금지 컨벤션 준수 확인됨) → `Propagation.NOT_SUPPORTED` 추가 조치 불필요

**Checkpoint**: ✅ `cd SSAju && ./gradlew clean test` BUILD SUCCESSFUL 확인 (`fix/us3-company-info-retry` 브랜치)

---

## Phase 6: User Story 4 - 신뢰할 수 있는 출처만 API에 접근 (Priority: P2)

**Goal**: CORS `allowedHeaders`를 화이트리스트로 좁힘(A3)

**Independent Test**: 화이트리스트 밖 헤더의 preflight 거부, `Authorization`/`Content-Type`만으로는 정상 동작 (quickstart.md "US4")

### Tests for User Story 4

- [x] T027 [P] [US4] MockMvc 테스트 `SSAju/src/test/java/ssafy/SSAju/config/SecurityConfigCorsTest.java` — 임의 헤더 preflight 거부, 화이트리스트 헤더는 허용 확인

### Implementation for User Story 4

- [x] T028 [US4] `config/SecurityConfig.java` `corsConfigurationSource()` 수정 — `allowedHeaders`를 `["*"]`에서 `List.of("Authorization", "Content-Type")`로 변경 — *구현 중 화이트리스트를 `cors.allowed-headers`로 `application.yaml` 외부화(PR 리뷰 반영)*

**Checkpoint**: `./gradlew test` 통과

---

## Phase 7: User Story 5 - 동시 요청에도 중복 없이 안전하게 자원 생성 (Priority: P2)

**Goal**: DB 예외 기반 재시도/흡수 방식을 Redisson 분산락으로 통일(Redis 항목 #2, C2)

**Independent Test**: 동일 키에 대한 동시 다건 요청이 정확히 1건의 정본만 생성하고, 진짜 무결성 위반은 여전히 오류로 구분됨 (quickstart.md "US5")

### Tests for User Story 5

- [x] T029 [P] [US5] `SSAju/src/test/java/ssafy/SSAju/concurrency/SajuResultConcurrencyTest.java` — 동일 `userProfileId`에 대해 N개 동시 요청 시 `saju_result` 행이 정확히 1개인지 확인
- [x] T030 [P] [US5] `SSAju/src/test/java/ssafy/SSAju/concurrency/UserProfileConcurrencyTest.java` — 동일 생년월일시 조합 동시 생성 시 `user_profile` 행 1개 확인
- [x] T031 [P] [US5] `SSAju/src/test/java/ssafy/SSAju/concurrency/CompanyCompatibilityConcurrencyTest.java` — 동일 (프로필, 회사, 직군) 조합 동시 생성 시 `company_compatibility` 행 1개 확인
- [x] T032 [P] [US5] `SSAju/src/test/java/ssafy/SSAju/concurrency/ConsultationConcurrencyTest.java` — 동일 (정본, 월) 조합 동시 생성 시 `career_consultation` 행 1개 확인

### Implementation for User Story 5

- [x] T033 [US5] `career/provider/SajuResultProvider.java` `findOrCreate`에 `@DistributedLock(key = "lock:saju-result:{userProfileId}")` 적용, `insertOrIgnore` 기반 흡수 로직 제거하고 락 보호 하에서 단순 조회-후-생성으로 변경; `repository/SajuResultJdbcRepository.java`의 `insertOrIgnore` 메서드 및 T049 TODO 주석 블록 제거(F 일부) — *`SajuResultJdbcRepository`가 이 메서드 하나만 가진 클래스여서 파일째 삭제. `SajuResultWriteService.saveNewResultWithChildren`도 `saveNewResult`로 단순화*
- [x] T034 [US5] `career/provider/UserProfileProvider.java` `findOrCreate`에 `@DistributedLock(key = "lock:user-profile:{birthDate}:{birthTime}")` 적용, `DataIntegrityViolationException` catch는 진짜 무결성 위반 전용으로 단순화
- [x] T035 [US5] `service/CompanyMatchingService.java` 궁합 생성 경로에 `@DistributedLock(key = "lock:company-compatibility:{userProfileId}:{companyName}:{targetRoleCategory}")` 적용; `repository/CompanyCompatibilityJdbcRepository.java`의 `DuplicateKeyException` 처리를 진짜 위반 전용으로 단순화 — *계획 대비 범위 확장: 같은 클래스 내 self-invocation에서는 `@DistributedLock`을 가로채는 Spring AOP 프록시가 작동하지 않으므로(`ConsultationSaveService`/`ConsultationInsertService`와 동일한 이유), 락 없는 1차 캐시 조회(`CompanyMatchingService`)와 락+쿼터+사주계산/AI/저장 구간을 신규 `service/CompanyCompatibilityLockedAnalysisService.java`로 분리. 락 획득 후 캐시 재확인(더블체크락)까지 도입해 대기 중이던 요청의 쿼터 이중 차감을 방지(US2 T024 재조정 완료)*
- [x] T036 [US5] `service/ConsultationSaveService.java` `insertOrRecoverOnConflict`에 `@DistributedLock(key = "lock:career-consultation:{sajuResultId}:{yearMonth}")` 적용, 제약조건 이름 문자열 분기 로직 제거
- [x] T037 [US5] `service/ConsultationInsertService.java`의 `REQUIRES_NEW` 트랜잭션 분리 우회 로직 제거(락이 충돌 자체를 막으므로 단순 트랜잭션으로 축소) — *`DistributedLockAspect`가 `HIGHEST_PRECEDENCE`로 트랜잭션 어드바이저보다 바깥쪽에 위치해 `@DistributedLock`+`@Transactional`을 한 메서드에 함께 선언 가능함을 확인 → `ConsultationInsertService` 및 도달 불가능해진 `ConsultationRecoveryFailedException` 파일째 삭제*

**Checkpoint**: `./gradlew test` 통과 — 분산락 동시성 테스트 포함 전체 통과 확인 (`feat/us5-distributed-lock-concurrency` 브랜치)

---

## Phase 8: User Story 6 - 사주 정본 데이터의 소유권과 캐시 분리 (Priority: P3)

**Goal**: `SajuResult`에서 `user_id` FK 제거 후 `user_profile_id` 단독 유니크로 전환, `UserSajuAccess` 매핑 테이블 신설, `FeedbackService` 소유권 검증 이관, `AnalysisType`/`FeedbackType` 통합(B1, B2)

**Independent Test**: 서로 다른 두 사용자가 동일 생년월일시로 요청 시 동일 정본 재사용 + 각자 매핑을 통해서만 접근, 정본 최초 생성이 쿼터를 소진하지 않음 (quickstart.md "US6")

### Tests for User Story 6

- [ ] T038 [P] [US6] `SSAju/src/test/java/ssafy/SSAju/service/SajuResultOwnershipTest.java` — 동일 생년월일시의 두 사용자가 같은 `SajuResult.id`를 참조하고 각자 별도의 `UserSajuAccess` 행을 갖는지 확인
- [ ] T039 [P] [US6] `SSAju/src/test/java/ssafy/SSAju/service/FeedbackServiceOwnershipTest.java` — `UserSajuAccess` 매핑이 없는 사용자의 피드백/상담 접근이 거부되는지 확인
- [ ] T040 [P] [US6] `SSAju/src/test/java/ssafy/SSAju/service/CareerFortuneQuotaTest.java` — 정본 최초 생성이 일일 쿼터를 차감하지 않는지 확인

### Implementation for User Story 6

- [ ] T041 [US6] `career/entity/SajuResult.java` 수정 — `user`(`@ManyToOne` FK `user_id`) 필드 제거, 기존 복합 유니크(`user_id, user_profile_id`) 대신 `user_profile_id` 단독 유니크로 변경
- [ ] T042 [US6] `career/entity/UserSajuAccess.java` 신규 생성 — `id`, `user`(FK, LAZY), `sajuResult`(FK, LAZY), `createdAt`(`@CreatedDate`), 유니크 `(user_id, saju_result_id)` (Lombok 규칙: `@Getter`+`@NoArgsConstructor(PROTECTED)`+`@Builder`, equals/hashCode ID 수기)
- [ ] T043 [P] [US6] `repository/UserSajuAccessRepository.java` 신규 생성 — `existsByUserIdAndSajuResultId`, `findByUserId` 등
- [ ] T044 [US6] (Git 커밋 대상 제외, 스크래치패드에만 생성) 운영 DB 마이그레이션 스크립트 작성 — 중복 정본 그룹별 생존 정본 선정 → 하위 테이블(`career_consultation` 등)의 `saju_result_id` UPDATE → `UserSajuAccess` 백필 INSERT → 중복 행 삭제 → `user_id` 컬럼/제약 DROP → `user_profile_id` 단독 유니크 생성 (data-model.md 마이그레이션 순서 그대로 반영, **이 파일은 git add 대상에서 제외**)
- [ ] T045 [US6] `service/CareerFortuneService.java` 수정 — Quota 차감 로직 완전 제거, 정본 최초 생성/최초 접근 시 `UserSajuAccessRepository`로 매핑 행 생성
- [ ] T046 [US6] `service/FeedbackService.java` 수정 — 기존 `SajuResult.user_id` 기반 소유권 검증을 `UserSajuAccessRepository.existsByUserIdAndSajuResultId` 기반으로 전면 교체
- [ ] T046-1 [US6] (선행 마이그레이션) `user_satisfaction_feedback` 테이블의 `feedback_type` 컬럼 데이터 마이그레이션 — 기존 `CAREER_TIMING`, `CONSULTATION`, `COMPATIBILITY` 값을 각각 `SAJU`, `CAREER_CONSULTATION`, `COMPANY_COMPATIBILITY`로 변환하거나, 역호환성을 위해 별도 변환 로직을 `UserSatisfactionFeedback` 엔티티의 getter에 임시 적용(예: `@Convert`를 사용한 JPA AttributeConverter). 마이그레이션은 Git 커밋 범위 밖(운영 별도 스크립트)이지만, 코드에서는 legacy 값 처리(backward compatibility) 또는 완전한 사전 변환 중 하나를 선택해 T047 실행 시 즉시 기존 행도 읽을 수 있도록 보장할 것.
- [ ] T047 [US6] `career/enums/FeedbackType.java` 삭제 및 다음 참조 파일들을 `career/enums/AnalysisType.java`로 치환(매핑: `CAREER_TIMING→SAJU`, `CONSULTATION→CAREER_CONSULTATION`, `COMPATIBILITY→COMPANY_COMPATIBILITY`): `career/entity/UserSatisfactionFeedback.java`, `admin/controller/AdminFeedbackController.java`, `admin/service/AdminFeedbackService.java`, `admin/dto/FeedbackListDTO.java`, `admin/dto/FeedbackStatDTO.java`, `admin/repository/AdminFeedbackQueryRepository.java`. **⚠️ T046-1 완료 후 진행**. **C1 선행 결합**: 같은 작업 단위에서 `controller/FeedbackController.java`→`career/controller/FeedbackController.java`, `dto/request/SatisfactionFeedbackRequest.java`→`career/dto/request/SatisfactionFeedbackRequest.java`, `dto/response/SatisfactionFeedbackResponse.java`→`career/dto/response/SatisfactionFeedbackResponse.java`로 함께 이동한다(패키지 이동과 Enum 치환을 서로 다른 Phase에서 나눠 하면 동일 파일에 대한 "수정 후 이동"으로 git 이력이 흩어지고 중간 빌드 리스크가 커지므로, 피드백 관련 파일만 여기서 선제적으로 이동 — 나머지 C1 대상은 Phase 11에서 그대로 진행)

**Checkpoint**: `./gradlew test` 통과 — 마이그레이션 스크립트는 실행하지 않고 존재만 확인(운영 적용은 배포 절차 별도)

---

## Phase 9: User Story 7 - 회원가입 시 태어난 시간(선택) 반영 (Priority: P3)

**Goal**: 가입 시 `birthDate`(필수)/`birthTime`(선택) 수집, 시간 미입력 시 더미값으로 FastAPI 호출 후 시간 기반 결과 필드 제외(#5)

**Independent Test**: `birthTime` 없이 가입/분석 성공, 응답에서 시간 기반 필드 제외; 입력 시 전체 필드 포함 (quickstart.md "US7")

### Tests for User Story 7

- [ ] T048 [P] [US7] `SSAju/src/test/java/ssafy/SSAju/service/AuthServiceSignupBirthTimeTest.java` — `birthTime` 없이 가입 성공 및 `null` 저장 확인
- [ ] T049 [P] [US7] `SSAju/src/test/java/ssafy/SSAju/service/AnalysisResultMaskingServiceTest.java` — `birthTime`이 없을 때 시간 기반 필드가 마스킹/제외되는지, 있을 때는 전체 필드가 유지되는지 확인

### Implementation for User Story 7

- [ ] T050 [US7] `dto/request/SignupRequest.java` 수정 — `birthDate`(`@NotNull LocalDate`), `birthTime`(`LocalTime`, 어노테이션 없이 선택값) 필드 추가
- [ ] T051 [US7] `entity/User.java` 수정 — `birthDate`(`nullable=false`), `birthTime`(`nullable=true`) 컬럼 추가
- [ ] T052 [US7] `service/AuthService.java` `signup()` 수정 — 요청의 `birthDate`/`birthTime`을 `User` 엔티티에 저장
- [ ] T053 [US7] 더미 시간 상수 추가(예: 기존 `CompatibilityConstants.DEFAULT_FOUNDING_TIME` 관례를 따르는 신규 상수, `"12:00"`) 및 `birthTime`이 없을 때 FastAPI 호출 경로(`career/provider/UserProfileProvider.java` 또는 관련 서비스)에서 더미값 사용하도록 연결
- [ ] T054 [US7] `service/AnalysisResultMaskingService.java` 확장 — 기존 책임을 먼저 확인한 뒤, `birthTime`이 없는 소스에 대해 시(時) 기반 파생 필드(시주/시 기반 십신·지장간 세부값 등, `contracts/career-quota-and-retry.md` 목록 참고)를 분석 응답에서 제외하는 로직 추가. **직렬화 하드닝**: 단순히 필드를 `null`로 채우면 Jackson이 `"필드명": null`을 그대로 JSON에 노출하므로, 해당 필드를 갖는 응답 record(또는 그 상위 response record)에 `@JsonInclude(JsonInclude.Include.NON_NULL)`을 적용해 값이 없을 때 키 자체가 응답 JSON에서 누락되도록 처리

**Checkpoint**: `./gradlew test` 통과

---

## Phase 10: User Story 8 - 관리자 계정 활동에 대한 완전한 감사 추적 (Priority: P3)

**Goal**: 관리자 로그인 성공/실패 모두 감사로그 기록, admin 서비스가 admin 전용 리포지토리만 사용(G1, C4)

**Independent Test**: 성공/실패 로그인 시도 각각 감사로그에 기록됨 (quickstart.md "US8")

### Tests for User Story 8

- [ ] T055 [P] [US8] `SSAju/src/test/java/ssafy/SSAju/admin/service/AdminAuthenticationServiceAuditTest.java` — 성공/실패 각각 감사로그 이벤트가 기록되는지 확인

### Implementation for User Story 8

- [ ] T056 [US8] `admin/service/AdminAuthenticationService.java` 수정 — 최상위 `repository/UserRepository` 대신 admin 전용 리포지토리 사용(필요 시 `admin/repository/AdminUserAuthQueryRepository.java` 신규 생성)(C4)
- [ ] T057 [US8] `admin/service/AdminAuthenticationService.java` `validateAdminCredentials()` 수정 — 성공/실패 경로 각각에서 감사로그 이벤트 기록(기존 `annotation/@AuditLog` + `aspect/AuditLoggingAspect.java` 패턴 재사용, 실패 시에도 기록되도록 트랜잭션 경계 고려)

**Checkpoint**: `./gradlew test` 통과

---

## Phase 11: User Story 9 - 코드베이스 구조 일관성 및 유지보수성 확보 (Priority: P4)

**Goal**: 패키지 구조(C1), 빈 설정 클래스 삭제(C6), 비동기 예외 처리(C8), 잔여 `Clock` 미적용 지점(C7), DTO 검증 누락(E1/E2), 예외 계층 편입(D1), 죽은 코드(F), 감사로그 공통 유틸(G2), 페이지네이션 근거 문서화(H1), N+1 쿼리 수정(H2)을 정리

**Independent Test**: 정적 검증 — 패키지 위치, 중복 로직 제거, 필수 검증 어노테이션 존재, 죽은 코드 미존재를 코드베이스에서 직접 확인 (quickstart.md "US9")

### Implementation for User Story 9

- [ ] T058 [P] [US9] `controller/CareerTimingController.java`, `controller/CompatibilityController.java`, `controller/ConsultationController.java`를 `career/controller/`로 이동, 패키지 선언 및 import 갱신(C1) — `FeedbackController.java`는 T047에서 이미 이동 완료
- [ ] T059 [P] [US9] `dto/request/CareerTimingRequest.java`, `CompatibilityRequest.java`, `ConsultationRequest.java` 및 `dto/response/CareerTimingResponse.java`, `CompatibilityResponse.java`, `ConsultationResponse.java`를 `career/dto/request/`, `career/dto/response/`로 이동(C1) — `SatisfactionFeedbackRequest`/`Response`는 T047에서 이미 이동 완료. 이동 후 T064의 `CompatibilityRequest` 검증 추가 위치도 새 경로 기준으로 적용
- [ ] T060 [US9] 빈 클래스 `config/WebMvcConfig.java` 삭제(C6)
- [ ] T061 [US9] `config/AsyncConfig.java`에 `AsyncUncaughtExceptionHandler` 구현체 등록(C8)
- [ ] T062 [US9] 코드베이스 전수 검색(`Instant.now()`/`LocalDateTime.now()`)으로 잔여 시스템 시간 직접 호출 지점을 찾아 주입된 `Clock`으로 교체(C7, T018에서 다룬 `AuthService` 외 잔여 지점)
- [ ] T063 [P] [US9] `admin/dto/UsageAdjustmentRequestDTO.java`의 `action`/`amount` 필드에 `@NotNull` 추가(E1)
- [ ] T064 [P] [US9] (T059 이동 후) `career/dto/request/CompatibilityRequest.java`의 시간 관련 필드(`userBirthTime`, `companyFoundingTime` 등)에 `@NotNull` 추가(E2, 필수/선택 여부는 US7의 선택적 출생시간 정책과 충돌하지 않는 필드만 대상 — 사용자 궁합 분석 요청 자체의 필수 입력 검증)
- [ ] T065 [US9] `exception/FeedbackNotAllowedException.java`, `exception/TokenHashException.java`를 `exception/SajuException` 계층 하위로 편입, `handler/SajuGlobalExceptionHandler.java`에 `TokenHashException` 전용 `@ExceptionHandler` 추가(D1)
- [ ] T066 [P] [US9] 죽은 코드 삭제 — `exception/InvalidPasswordException.java` 및 `handler/SajuGlobalExceptionHandler.java` 내 대응 핸들러, 그 외 사용되지 않는 상수/리포지토리 메서드/DTO 전수 검색 후 삭제(F)
- [ ] T067 [US9] `entity/RefreshToken.java`, `repository/RefreshTokenRepository.java` 삭제(US1에서 Redis로 대체 완료 확인 후), `refresh_token` 테이블 DROP용 DDL을 별도 스키마 마이그레이션 파일로 준비(스키마 변경 스크립트는 데이터 마이그레이션과 달리 커밋 가능하나 실행은 배포 절차에 따름)
- [ ] T068 [US9] `admin/service/AdminUsageAdjustmentService.java`의 "커밋 이후 완료 로그 기록" 패턴을 공통 유틸(`admin/service/AdminAuditLogUtil.java` 등)로 추출하고, `AdminUsageAdjustmentService` 및 T057에서 작성한 `AdminAuthenticationService` 감사로그 기록 코드가 이 유틸을 사용하도록 리팩토링(G2)
- [ ] T069 [P] [US9] 서로 다른 4가지 페이지네이션 방식을 사용하는 컨트롤러(`admin/controller/*`, `controller/*` 전수 검색으로 식별)에 각 방식의 채택 이유를 주석으로 문서화(H1)
- [ ] T070 [US9] `admin/repository/AdminUserQueryRepository.java`의 `findUserById` 내 N+1 위험 쿼리를 조인/배치 조회로 분리·호이스팅(H2)

**Checkpoint**: `./gradlew test` 통과 — 모든 User Story가 구조적으로 정리된 상태로 함께 동작

---

## Phase 12: Polish & Cross-Cutting Concerns

**Purpose**: 전체 통합 검증 및 마무리

- [ ] T071 `cd SSAju && ./gradlew clean test` 실행 — 전체 스위트(단위+통합+동시성) `BUILD SUCCESSFUL` 확인
- [ ] T072 [P] `SSAju/CLAUDE.md` 갱신 — `career/controller`, `career/dto` 신설, Redis/Redisson 도입, `AbstractJwtValidationFilter` 등 아키텍처 변경 사항 반영
- [ ] T073 `quickstart.md`의 9개 User Story 시나리오를 로컬(`./gradlew bootRun` + Redis/MySQL 컨테이너)에서 수동 실행하여 최종 확인
- [ ] T074 최종 전수 검색 — `FeedbackType`, `InvalidPasswordException`, `entity/RefreshToken`(JPA), `insertOrIgnore` 관련 잔여 참조가 코드베이스에 없는지 확인

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 의존성 없음 — 즉시 시작 가능
- **Foundational (Phase 2)**: Setup 완료에 의존 — 모든 User Story를 차단(BLOCKS)
- **User Stories (Phase 3~11)**: 모두 Foundational 완료에 의존
  - 우선순위 순서(P1→P4)대로 순차 진행이 기본 전략(사용자가 지정한 [Redis 인프라]→A→B→C/D/E→F→G→H 순서와 일치)
  - 여력이 있다면 동일 우선순위 내 스토리는 병렬 진행 가능(예: US1과 US2는 모두 P1이지만 서로 다른 파일을 다룸)
- **Polish (Phase 12)**: 모든 User Story 완료에 의존

### User Story Dependencies

- **US1(P1)**: Foundational 이후 즉시 시작 가능, 다른 스토리에 의존 없음
- **US2(P1)**: Foundational 이후 즉시 시작 가능, US1과 파일이 겹치지 않아 독립적. `CompanyMatchingService.analyzeCompatibility`의 쿼터 로직(T024)은 이후 US5(T035)의 분산락 적용 시 반드시 재조정되어야 하는 **전방 의존**이 있음(아래 US5 항목 참고). `ConsultationService.getCareerConsultation`의 쿼터 로직(T024-1)은 US5의 분산락 대상(`ConsultationSaveService`, T036)이 저장 단계에만 적용되므로 이런 전방 의존이 없음 — T024-1/T024-2는 다른 태스크와 독립적으로 아무 때나 진행 가능
- **US3(P2)**: Foundational 이후 시작 가능, 독립적
- **US4(P2)**: Foundational 이후 시작 가능, 독립적
- **US5(P2)**: Foundational(특히 T007 `DistributedLock`) 이후 시작 가능. `CompanyMatchingService`에 락을 적용하는 T035는 US2(T024)의 쿼터 차감/복원 로직과 같은 메서드를 다루므로, 락+캐시 재확인(double-checked locking) 도입 시 쿼터 차감 시점을 반드시 재검토해야 함(T024 참고) — 이 부분만 US2에 약한 후행 의존이 있고 나머지는 독립적
- **US6(P3)**: Foundational 이후 시작 가능하나, US5에서 `SajuResultProvider`/`ConsultationSaveService`에 분산락을 적용해 둔 상태에서 진행하는 것을 권장(정본 생성 경로를 두 번 건드리지 않기 위함) — 강한 기술적 의존은 아니나 순서 권장
- **US7(P3)**: Foundational 이후 시작 가능, 독립적(단 `AnalysisResultMaskingService` 기존 책임 확인 필요)
- **US8(P3)**: Foundational 이후 시작 가능, 독립적
- **US9(P4)**: 나머지 모든 User Story의 변경 결과물(파일 위치, 예외 클래스, DTO 등)을 대상으로 하는 정리 작업이므로 **US1~US8 완료 후 진행 권장**(특히 T059는 US7/US9의 `CompatibilityRequest` 검증 순서와 맞물림)

### Within Each User Story

- 테스트 우선 작성 후 실패 확인 → 구현
- 엔티티/저장소 변경 → 서비스 로직 변경 → 컨트롤러/필터 변경 순
- 각 스토리 완료 시 `./gradlew test`로 회귀 확인 후 다음 우선순위로 이동

### Parallel Opportunities

- Setup의 T002, T003은 병렬 가능
- Foundational의 T005, T006, T009, T010은 병렬 가능(T004 완료 후)
- US1~US4는 서로 다른 파일을 다루므로, 인력이 있다면 Foundational 완료 후 동시에 착수 가능
- 각 User Story 내 `[P]` 표시된 테스트/신규 파일 생성 태스크는 병렬 가능

---

## Parallel Example: User Story 1

```bash
# US1 테스트 3종 병렬 작성
Task: "통합 테스트 AuthTokenSecurityIntegrationTest.java"
Task: "RefreshTokenRedisRepositoryTest.java"
Task: "TokenValidationFilterTest.java"

# US1 신규 컴포넌트 병렬 생성(서로 다른 파일)
Task: "RefreshTokenRedisRepository.java 생성"
Task: "AccessTokenBlacklistService.java 생성"
```

---

## Implementation Strategy

### MVP First (User Story 1 + 2, 모두 P1)

1. Phase 1(Setup) 완료
2. Phase 2(Foundational) 완료 — **필수 선행**
3. Phase 3(US1: 세션 보안) 완료 → 독립 검증
4. Phase 4(US2: 쿼터 보존) 완료 → 독립 검증
5. 여기까지가 "실제 장애/보안 버그"의 핵심 MVP — 배포/데모 가능

### Incremental Delivery

1. Setup + Foundational → 인프라 준비 완료
2. US1 → US2 (모두 P1, 실제 결함 수정) → 각 완료 시 `./gradlew test` + 데모
3. US3 → US4 → US5 (P2, 나머지 A/동시성) → 각 완료 시 검증
4. US6 → US7 → US8 (P3, 데이터 모델/기능/감사) → 각 완료 시 검증
5. US9 (P4, 구조 정리) → 전체 정리 후 최종 검증
6. Polish(Phase 12) → 전체 통합 확인

### Parallel Team Strategy

여러 담당자가 있는 경우:

1. 전체가 Setup + Foundational을 함께 완료
2. Foundational 완료 후:
   - 담당자 A: US1(세션 보안) → US6(정본/소유권, US1의 Redis 인프라 재사용)
   - 담당자 B: US2(쿼터) → US5(분산락, Foundational의 DistributedLock 재사용)
   - 담당자 C: US3(재시도) → US4(CORS) → US8(감사로그)
   - 담당자 D: US7(출생시간) → 이후 US9(구조 정리)에 합류
3. US9는 다른 스토리들의 산출물을 대상으로 하므로 팀 전체가 마지막에 함께 마무리

---

## Notes

- `[P]` 태스크 = 서로 다른 파일, 의존성 없음
- `[Story]` 라벨은 각 태스크의 추적성을 위해 부여됨
- 각 User Story는 독립적으로 완료·테스트 가능해야 함
- 구현 전 테스트가 실패하는지 먼저 확인
- 각 태스크 또는 논리적 그룹 단위로 커밋(`skills/git-workflow.md` 규칙 준수, 커밋 메시지에 `[Test Passed]` 푸터 필수)
- 체크포인트마다 스토리 단독 동작을 검증 후 정지 가능
- 지양할 것: 모호한 태스크, 동일 파일 동시 수정 충돌, 스토리 간 독립성을 깨는 교차 의존
