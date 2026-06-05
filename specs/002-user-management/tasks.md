# Tasks: User Management & Data Integration (Phase 2)

**Feature**: User Management (사용자 관리 및 분석 데이터 통합)
**Input**: `/specs/002-user-management/spec.md`, `/specs/002-user-management/plan.md`
**Architecture**: Spring Boot 4.0.5 + MySQL + Spring Security (JWT)

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: User story label (US1-US7, no label for setup/foundational)
- **Paths**: SSAju/ 프로젝트 기준

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and Spring Boot configuration

- [x] T001 Initialize Spring Security PasswordEncoder configuration in SSAju/src/main/java/ssafy/SSAju/config/SecurityConfig.java
- [x] T002 [P] Create custom exception hierarchy (User, Authentication, DailyApiUsage exceptions) in SSAju/src/main/java/ssafy/SSAju/exception/

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure and data model that ALL user stories depend on

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Database & Data Model

- [x] T003 [P] Create User entity with PasswordEncoder (bcrypt) fields in SSAju/src/main/java/ssafy/SSAju/entity/User.java
  - Fields: id, email (UNIQUE), password_hash, name, role (ENUM: USER, ADMIN), status, last_login_at, terms_agreed_at, privacy_agreed_at, deleted_at, created_at, updated_at
  - Soft delete annotation (@SQLRestriction 사용, @SQLDelete 금지)

- [x] T004 [P] Create RefreshToken entity in SSAju/src/main/java/ssafy/SSAju/entity/RefreshToken.java
  - Fields: id, user_id (FK), token_hash (UNIQUE), expires_at, revoked_at, created_at

- [x] T005 [P] Create DailyApiUsage entity with UNIQUE constraint in SSAju/src/main/java/ssafy/SSAju/entity/DailyApiUsage.java
  - Fields: id, user_id (FK), request_count, usage_date (KST 기준), created_at
  - **CRITICAL**: UNIQUE(user_id, usage_date) 제약 조건 필수 (Race Condition 방지)

- [x] T006 [P] Create LoginAttempt entity in SSAju/src/main/java/ssafy/SSAju/entity/LoginAttempt.java
  - Fields: id, email, success, ip_address, failure_reason (ENUM: SUCCESS, INVALID_EMAIL, WRONG_PASSWORD, UNKNOWN), attempted_at
  - **Purpose**: 클라이언트는 통합 응답(User Enumeration 방지), 서버 로그는 원인 상세 기록

- [x] T006-1 [P] Create UserSatisfactionFeedback entity in SSAju/src/main/java/ssafy/SSAju/entity/UserSatisfactionFeedback.java
  - Fields: id, user_id (FK), feedback_type (ENUM: SAJU, CAREER_FORTUNE, COMPANY_COMPATIBILITY), saju_result_id (FK, nullable), career_fortune_result_id (FK, nullable), company_compatibility_result_id (FK, nullable), satisfaction_status (ENUM), feedback_content (String, nullable), created_at, updated_at
  - Relationship: @ManyToOne User (user_id FK)
  - Used by T003 (delete/mask on account deletion)

### Repository & Database Setup

- [x] T007 [P] Create repository interfaces in SSAju/src/main/java/ssafy/SSAju/repository/
  - UserRepository
  - RefreshTokenRepository
  - DailyApiUsageRepository
  - LoginAttemptRepository
  - UserSatisfactionFeedbackRepository (added for T004-1)

- [x] T008 Create SQL schema migration script in SSAju/src/main/resources/db/migration/
  - Include UNIQUE INDEX `idx_daily_api_usage_user_date` on (user_id, usage_date)
  - Include all table definitions for User, RefreshToken, DailyApiUsage, LoginAttempt, UserSatisfactionFeedback
  - **LoginAttempt table**: id, email, success (BOOLEAN), ip_address (VARCHAR(45)), failure_reason (ENUM: 'SUCCESS', 'INVALID_EMAIL', 'WRONG_PASSWORD', 'UNKNOWN'), attempted_at (TIMESTAMP)
  - UserSatisfactionFeedback table: user_id FK, feedback_type ENUM, saju/career_fortune/company_compatibility result IDs, satisfaction_status, feedback_content

### Security & Token Framework

- [x] T009 Implement JWT utility (token generation/validation) in SSAju/src/main/java/ssafy/SSAju/util/JwtUtil.java
  - Generate AccessToken (1시간), RefreshToken (7일)
  - Validate and extract claims

- [x] T010 [P] Configure Spring Security filter chain in SSAju/src/main/java/ssafy/SSAju/config/SecurityConfig.java
  - JWT validation filter
  - CORS configuration
  - PasswordEncoder bean (BCrypt)

- [x] T010-1 [P] JWT 에러 처리 및 예외 프레임워크 구현 (Security Components) in SSAju/src/main/java/ssafy/SSAju/security/
  - **JwtExceptionFilter**: `JwtAuthenticationFilter` 앞단에 배치하여 토큰 검증 시 발생하는 예외(`ExpiredJwtException`, `SignatureException`, `MalformedJwtException` 등)를 캐치하고 HTTP 401 JSON 응답으로 반환 (@ControllerAdvice가 Filter 예외를 잡지 못하는 문제 해결)
  - **JwtAuthenticationEntryPoint**: 인증되지 않은 사용자 접근 시(401) 일관된 JSON 에러 응답을 반환하도록 `AuthenticationEntryPoint` 구현
  - **JwtAccessDeniedHandler**: 권한이 부족한 사용자 접근 시(403) 일관된 JSON 에러 응답을 반환하도록 `AccessDeniedHandler` 구현
  - **RefreshToken 예외 처리**: `RefreshToken.isExpired()` 경계값 처리 (만료 시간과 동일한 경우도 만료로 간주)
  - **보안성 강화**: SecurityContext에 유효하지 않은 토큰 전파를 방지하고, 클라이언트에게 내부 예외 상세(Stack trace 등)가 노출되지 않도록 마스킹 처리된 명확한 에러 메시지 제공

- [x] T011 Implement HttpOnly cookie utility in SSAju/src/main/java/ssafy/SSAju/util/CookieUtil.java
  - Set RefreshToken with HttpOnly, Secure, SameSite attributes

### DailyApiUsage Framework (Race Condition Prevention)

- [x] T012 Implement DailyApiUsageService with Atomic UPDATE + Exception handling in SSAju/src/main/java/ssafy/SSAju/service/DailyApiUsageService.java
  - Method: checkAndIncrementDailyUsage(Long userId)
  - Atomic UPDATE: update where request_count < 3
  - DataIntegrityViolationException catch for Race Condition handling
  - KST timezone support (LocalDate.now(ZoneId.of("Asia/Seoul")))

- [x] T013 Implement APIUsageInterceptor in SSAju/src/main/java/ssafy/SSAju/handler/APIUsageInterceptor.java
  - Pre-request check for daily API limit
  - HTTP 429 response when limit exceeded

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - 회원가입 (Priority: P1) 🎯 MVP

**Goal**: 신규 사용자가 이메일과 비밀번호로 계정 생성

**Independent Test**: 회원가입 → 중복 이메일 방지 → 약관 동의 확인 → 로그인 페이지 리다이렉트

### Implementation for US1

- [x] T014 [US1] Create SignupRequest DTO in SSAju/src/main/java/ssafy/SSAju/dto/request/SignupRequest.java
  - Fields: email, password, name, termsAgreed, privacyAgreed

- [x] T015 [US1] Create AuthService in SSAju/src/main/java/ssafy/SSAju/service/AuthService.java
  - Method: signup(SignupRequest) with PasswordEncoder.encode()
  - Validation: email uniqueness, password strength (8자 이상), terms agreement
  - Record terms_agreed_at, privacy_agreed_at

- [x] T016 [US1] Create AuthController with POST /api/auth/signup in SSAju/src/main/java/ssafy/SSAju/controller/AuthController.java
  - Input validation via @Valid
  - Error handling: duplicate email, weak password, missing consent
  - Response: 201 Created + redirect URL

- [x] T017 [US1] Add logging for signup events in AuthService

**Checkpoint**: User Story 1 (회원가입) is complete and independently testable

---

## Phase 4: User Story 2 - 로그인 (Priority: P1)

**Goal**: 등록된 사용자가 AccessToken + RefreshToken 발급받고 인증 상태 진입

**Independent Test**: 로그인 성공 → AccessToken(1시간) + RefreshToken(7일) 발급 → 토큰으로 API 호출

### Implementation for US2

- [x] T018 [US2] Create LoginRequest DTO in SSAju/src/main/java/ssafy/SSAju/dto/request/LoginRequest.java
  - Fields: email, password

- [x] T019 [US2] Create AuthTokenResponse DTO in SSAju/src/main/java/ssafy/SSAju/dto/response/AuthTokenResponse.java
  - Fields: accessToken, accessTokenExpiresIn

- [x] T020 [US2] Implement AuthService.login() in SSAju/src/main/java/ssafy/SSAju/service/AuthService.java
  - PasswordEncoder.matches(password, user.getPasswordHash())
  - Generate AccessToken (JwtUtil)
  - Save RefreshToken to DB (token_hash)
  - Update user.last_login_at

- [x] T021 [US2] Enhance AuthController with POST /api/auth/login in SSAju/src/main/java/ssafy/SSAju/controller/AuthController.java
  - Set HttpOnly RefreshToken cookie (CookieUtil)
  - Return AccessToken in response body
  - Error: "이메일 또는 비밀번호가 일치하지 않습니다"

- [x] T022 [US2] Implement EventPublisher-based login attempt logging in SSAju/src/main/java/ssafy/SSAju/service/AuthService.java
  - Pattern: AuthService에서 ApplicationEventPublisher.publishEvent(LoginAttemptEvent) 발행
  - failure_reason에 INVALID_EMAIL, WRONG_PASSWORD, SUCCESS 등 상세히 기록
  - 클라이언트 응답은 "이메일 또는 비밀번호가 일치하지 않습니다" (User Enumeration 방지)
  - @EventListener (@TransactionalEventListener 또는 @Async)에서 DB 저장은 T022-1에서 구현

- [x] T022-1 [US2] Create LoginAttemptEvent and listener for async logging in SSAju/src/main/java/ssafy/SSAju/event/
  - Event class: LoginAttemptEvent (email, success, failureReason ENUM, ipAddress, timestamp)
  - Listener class: LoginAttemptEventListener with @EventListener @Async @Transactional(REQUIRES_NEW)
  - Purpose: 로깅으로 인한 로그인 응답 지연 방지, 비동기 DB 저장

- [x] T022-2 [US2] Create ClientIpUtil for real IP extraction in SSAju/src/main/java/ssafy/SSAju/util/ClientIpUtil.java
  - Method: getClientIp(HttpServletRequest request) → String
  - Header 순서대로 검사: X-Forwarded-For → CF-Connecting-IP → X-Real-IP → request.getRemoteAddr()
  - Purpose: 로드밸런서/리버스 프록시 뒤에서도 실제 클라이언트 IP 추출 (운영 환경 대비)
  - T004에서 ipAddress 추출 시 이 유틸 사용

**Checkpoint**: User Story 2 (로그인) is complete and independently testable

---

## Phase 5: User Story 3 - 로그아웃 (Priority: P1)

**Goal**: 로그인 상태 종료, 토큰 무효화

**Independent Test**: 로그아웃 → 이전 토큰 무효화 → 401 Unauthorized 반환

### Implementation for US3

- [x] T023 [US3] Implement AuthService.logout() in SSAju/src/main/java/ssafy/SSAju/service/AuthService.java
  - Extract userId from SecurityContext (로그인된 사용자 확인)
  - Mark RefreshToken.revoked_at = NOW()
  - Invalidate AccessToken (store in blacklist if needed)

- [x] T024 [US3] Implement TokenValidationFilter to check revoked RefreshToken in SSAju/src/main/java/ssafy/SSAju/filter/TokenValidationFilter.java
  - Reject requests with revoked tokens (401 Unauthorized)

- [x] T025 [US3] Enhance AuthController with POST /api/auth/logout in SSAju/src/main/java/ssafy/SSAju/controller/AuthController.java
  - Extract AccessToken from Authorization header and RefreshToken from HttpOnly cookie
  - Call AuthService.logout(userId)
  - Clear RefreshToken cookie
  - Return success response

**Checkpoint**: User Story 3 (로그아웃) is complete and independently testable

---

## Phase 6: User Story 4 - 로그인 상태 유지 (Priority: P2)

**Goal**: RefreshToken으로 만료된 AccessToken 자동 갱신

**Independent Test**: AccessToken 만료 → RefreshToken으로 갱신 요청 → 새 AccessToken 즉시 발급

### Implementation for US4

- [x] T026 [US4] Implement AuthService.refreshAccessToken() in SSAju/src/main/java/ssafy/SSAju/service/AuthService.java
  - Extract RefreshToken from HttpOnly cookie (직접 추출, DTO 불필요)
  - Validate RefreshToken (not revoked, not expired)
  - Extract userId from RefreshToken
  - Generate new AccessToken
  - Return in response

- [x] T027 [US4] Enhance AuthController with POST /api/auth/refresh in SSAju/src/main/java/ssafy/SSAju/controller/AuthController.java
  - Read RefreshToken from HttpOnly cookie (Request.getCookies())
  - Extract and validate token (no request body needed)
  - Call AuthService.refreshAccessToken()
  - Return new AccessToken in response body

**Checkpoint**: User Story 4 (로그인 상태 유지) is complete and independently testable

---

## Phase 7: User Story 5 - 일일 API 요청 제한 (Priority: P2)

**Goal**: 인증 사용자 하루 3회 분석 API 제한 (Race Condition 안전)

**Independent Test**: 3회 요청 성공 → 4번째 HTTP 429 → 자정 후 초기화

### Implementation for US5

- [x] T028 [US5] Enhance APIUsageInterceptor with DailyApiUsageService.checkAndIncrementDailyUsage() in SSAju/src/main/java/ssafy/SSAju/handler/APIUsageInterceptor.java
  - Extract userId from SecurityContext
  - Call service before API execution
  - Handle DailyLimitExceededException

- [x] T029 [US5] Create DailyLimitExceededException in SSAju/src/main/java/ssafy/SSAju/exception/DailyLimitExceededException.java

- [x] T030 [US5] Handle HTTP 429 in GlobalExceptionHandler
  - Map DailyLimitExceededException to 429 Conflict
  - Message: "하루 3회 분석 제한에 도달했습니다"

- [x] T031 [US5] [P] **Race Condition Test**: Verify DailyApiUsageService with DataIntegrityViolationException handling
  - 10 concurrent requests → exactly 3 succeed, 7 fail with 429
  - UNIQUE INDEX (user_id, usage_date) must be present in DB

**Checkpoint**: User Story 5 (일일 API 요청 제한) is complete with Race Condition safety verified

---

## Phase 8: User Story 6 - 회원 탈퇴 (Priority: P2)

**Goal**: 사용자가 계정 삭제 (Soft Delete + 개인정보 마스킹)

**Independent Test**: 탈퇴 → 비밀번호 확인 → 계정 비활성화 → 이전 토큰 무효화 → 로그인 불가

### Implementation for US6

- [x] T032 [US6] Create DeleteUserRequest DTO in SSAju/src/main/java/ssafy/SSAju/dto/request/DeleteUserRequest.java
  - Fields: password (재확인용)

- [x] T033 [US6] Implement AuthService.deleteUser(Long userId, String password) in SSAju/src/main/java/ssafy/SSAju/service/AuthService.java
  - **중요**: @SQLDelete 사용 금지. 명시적 soft delete 처리만 사용
  - PasswordEncoder.matches() 비밀번호 재확인
  - User 엔티티 필드 직접 수정:
    - User.name = "탈퇴한 사용자"
    - User.email = "deleted_{userId}_{timestamp}@deleted.local" (UNIQUE 제약 충돌 방지)
    - User.deleted_at = NOW()
    - User.status = INACTIVE
  - userRepository.save(user) 호출 (JPA 기본 UPDATE 실행)
  - Mark all RefreshTokens as revoked

- [x] T034 [US6] Enhance User entity with @SQLRestriction in SSAju/src/main/java/ssafy/SSAju/entity/User.java
  - Add @SQLRestriction: WHERE deleted_at IS NULL (자동 필터링)
  - **주의**: @SQLDelete 어노테이션 제거 (명시적 service 로직에서만 soft delete 처리)

- [x] T035 [US6] Mask analysis results in AnalysisResultMaskingService in SSAju/src/main/java/ssafy/SSAju/service/AnalysisResultMaskingService.java
  - Find and mask SajuAnalysisResult, CareerFortuneResult, CompanyCompatibilityResult
  - Find and delete UserSatisfactionFeedback

- [x] T036 [US6] Enhance AuthController with DELETE /api/users/me in SSAju/src/main/java/ssafy/SSAju/controller/AuthController.java
  - Extract userId from SecurityContext (@Authenticated user)
  - Extract password from request body (DeleteUserRequest)
  - Call AuthService.deleteUser(userId, password)
  - Clear RefreshToken cookie
  - Return success response + redirect to login

**Checkpoint**: User Story 6 (회원 탈퇴) is complete with Soft Delete & masking

---

## Phase 9: User Story 7 - 마이페이지 (Priority: P2)

**Goal**: 사용자가 마이페이지에서 최근 1년 범위의 모든 분석 결과 통합 조회 및 관리

**Independent Test**: 마이페이지 접근 → 최근 1년 분석 결과 목록 표시 → 분석 클릭 → 상세 결과 + 만족도 조사 표시

### Implementation for US7

- [x] T037 [US7] Create UserAnalysisDto in SSAju/src/main/java/ssafy/SSAju/dto/response/UserAnalysisDto.java
  - Fields: type (SAJU/CAREER_FORTUNE/COMPANY_COMPATIBILITY), analysisId, targetName, birthDate, createdAt, satisfactionStatus

- [x] T038 [US7] Create AnalysisHistoryRepository with UNION query in SSAju/src/main/java/ssafy/SSAju/repository/AnalysisHistoryRepository.java
  - Query: SELECT UNION for SajuAnalysisResult, CareerFortuneResult, CompanyCompatibilityResult
  - Filter: created_at >= DATE_SUB(NOW(), INTERVAL 1 YEAR) (최근 1년 범위)
  - Order: created_at DESC
  - Pagination support

- [x] T039 [US7] Implement UserService.getUserAnalysisHistory() in SSAju/src/main/java/ssafy/SSAju/service/UserService.java
  - Call AnalysisHistoryRepository
  - Include satisfaction score if available
  - Apply filtering (분석 유형 필터)

- [x] T040 [US7] Create UserController with GET /api/mypage in SSAju/src/main/java/ssafy/SSAju/controller/UserController.java
  - Return user profile (name, joinDate, lastLoginAt)
  - Return analysis history (paginated)
  - Support filtering by analysis type

- [x] T041 [US7] Create MyPageResponse DTO in SSAju/src/main/java/ssafy/SSAju/dto/response/MyPageResponse.java
  - Fields: profile (UserProfile), analyses (List<UserAnalysisDto>), pagination (page, size, total, totalPages)

- [x] T041-1 [US7] Implement GET /api/mypage/analyses/{analysisId} in UserController for detailed analysis view
  - Fetch specific analysis result (SajuAnalysisResult OR CareerFortuneResult OR CompanyCompatibilityResult)
  - Include related UserSatisfactionFeedback data (satisfaction_status, feedback_content)
  - Return complete analysis data with metadata
  - File: SSAju/src/main/java/ssafy/SSAju/controller/UserController.java
  - Service: SSAju/src/main/java/ssafy/SSAju/service/UserService.java (new method: getAnalysisDetail)

**Checkpoint**: User Story 7 (마이페이지) is complete with list + detail + reanalysis

---

## Phase 10: Integration & Polish

**Purpose**: Cross-cutting concerns and system integration

- [x] T042 Implement logging for all authentication events in SSAju/src/main/java/ssafy/SSAju/aspect/AuditLoggingAspect.java
  - Sign-up, Login, Logout, Token Refresh, Delete User
  - Include userId, IP, timestamp (민감정보 제외)

- [x] T043 [P] Add validation constraints on all DTOs (@NotNull, @Email, @Size, etc.)

- [x] T044 [P] Setup integration tests for authentication flow in SSAju/src/test/java/ssafy/SSAju/integration/AuthIntegrationTest.java
  - Sign-up → Login → API call → Token refresh → Logout

- [x] T045 [P] Setup integration tests for daily API usage in SSAju/src/test/java/ssafy/SSAju/integration/DailyApiUsageIntegrationTest.java
  - Verify 3-request limit
  - Verify concurrent request handling (Race Condition test)
  - **Note**: MySQL Connector/J 9.x 호환성 버그 수정 (DailyApiUsageRepositoryImpl - no-op UPDATE 감지)

- [x] T046 [P] Setup integration tests for soft delete in SSAju/src/test/java/ssafy/SSAju/integration/SoftDeleteIntegrationTest.java
  - Verify user masking
  - Verify @SQLRestriction filtering

- [x] T047 [P] Modify SajuAnalysisResult entity to add user_id FK in SSAju/src/main/java/ssafy/SSAju/entity/SajuAnalysisResult.java (Phase 1 integration)
  - Add @ManyToOne User (user_id FK)
  - Ensure NOT NULL constraint in DB migration
  - Backward compatibility: migration script for existing records (set null to default user if needed)

- [x] T048-1 [P] Modify CareerFortuneResult entity to add user_id FK in SSAju/src/main/java/ssafy/SSAju/entity/CareerFortuneResult.java (Phase 1 integration)
  - Add @ManyToOne User (user_id FK)
  - Ensure NOT NULL constraint in DB migration

- [x] T048-2 [P] Modify CompanyCompatibilityResult entity to add user_id FK in SSAju/src/main/java/ssafy/SSAju/entity/CompanyCompatibilityResult.java (Phase 1 integration)
  - Add @ManyToOne User (user_id FK)
  - Ensure NOT NULL constraint in DB migration

- [ ] T048-3 Create database migration script (V2_Phase1Integration) in SSAju/src/main/resources/db/migration/
  - **[BLOCKER]** T049 and T050 depend on this; Phase 1 entities must exist first
  - ALTER TABLE saju_analysis_result ADD user_id BIGINT NOT NULL
  - ALTER TABLE career_fortune_result ADD user_id BIGINT NOT NULL
  - ALTER TABLE company_compatibility_result ADD user_id BIGINT NOT NULL
  - Add FOREIGN KEY constraints to user table
  - Add INDEX on user_id for query performance

- [On-hold] T049 Update Phase 1 AuthController/Service to automatically map user_id when saving analysis results
  - ⏸️ **Blocked by T048-3**: Phase 1 entities (SajuAnalysisResult, CareerFortuneResult, CompanyCompatibilityResult) not yet created
  - T048-3 must be completed before T049
  - Extract user_id from SecurityContext (authenticated user)
  - Pass to analysis service/repository
  - File: SSAju/src/main/java/ssafy/SSAju/career/service/ (coordinate with Phase 1)

- [On-hold] T050 Update CLAUDE.md with Phase 2 completion and next phase context
  - ⏸️ **Blocked by T049**: Phase 2 finalization depends on Phase 1 integration being complete
  - T048-3 must be completed before T049, and T049 must be completed before T050

---

## Dependencies & Execution Order

### Critical Path (Blocking):
1. **Phase 1**: Setup (T001-T002)
2. **Phase 2**: Foundational (T003-T013 + T006-1 UserSatisfactionFeedback)
   - All subsequent phases depend on this
3. **Phase 3**: User Story 1 회원가입 (T014-T017)
   - Blocks all login/token dependent stories
4. **Phase 1 Integration** (T047-T048-3): Modify Phase 1 entities to include user_id FK
   - MUST complete before US7 implementation (MyPage needs user_id mapping)

### Parallel Opportunities:

**After T013 + T006-1 (Foundational + UserSatisfactionFeedback complete)**:
- US1, US2 (T018-T022-2), US3 can start in parallel (T014-T025, including T022-1 & T022-2 for secure logging)
- US4 can start when US2 done (RefreshToken logic, T026-T027)
- US5 can start in parallel with US1-3 (no dependency, T028-T031)
- US6 can start when US1-3 done (delete requires working auth, T032-T036)
- **Note**: T022-1 (EventPublisher) & T022-2 (ClientIpUtil) are parallel to T020-T021 (로그인 구현)

**After T047-T048-3 (Phase 1 Integration complete)**:
- US7 (마이페이지) can start - needs user_id in SajuAnalysisResult, CareerFortuneResult, CompanyCompatibilityResult

**Recommended Sequential**:
- T001-T013 + T006-1 (Phase 1-2 Foundational, required first)
- T047-T048-3 (Phase 1 Integration, needed before US7)
- T014-T025 (Phase 3-5, P1 stories in parallel)
- T026-T041-2 (Phase 6-9, P2 stories in parallel)
- T042-T050 (Integration & Polish after core stories)

---

## MVP Scope (Recommended)

For **Phase 2 MVP** (Phase 1의존, Phase 1 구현 후):
- **Phase 2 Foundational**: 모두 포함 (T003-T013 + T006-1 UserSatisfactionFeedback)
- **User Story 1**: 회원가입 (T014-T017)
- **User Story 2**: 로그인 + 로깅 (T018-T022-2, EventPublisher + ClientIpUtil 포함)
- **User Story 3**: 로그아웃 (T023-T025)

**For MVP + Full US7 (마이페이지)**:
- All foundational (T003-T013 + T006-1)
- Phase 1 Integration: T047-T048-3 (user_id FK mapping)
- US1-US3: T014-T025
- US7 (마이페이지): T037-T041-2

**Total Tasks for MVP**: T003-T025 + T006-1 + T010-1 (25 tasks)
**Total Tasks for MVP + Full MyPage**: T003-T041-2 (includes Phase 1 integration)
**Estimated Timeline**: 2-3 weeks for experienced team

---

## Task Summary

**Total Tasks**: 58
**Setup/Foundational**: 15 (T001-T013 + T006-1 + T010-1)
**User Story 1 (회원가입)**: 4 (T014-T017)
**User Story 2 (로그인 + 보안 로깅)**: 7 (T018-T022-2, EventPublisher 패턴 + ClientIpUtil + failure_reason)
**User Story 3 (로그아웃)**: 3 (T023-T025)
**User Story 4 (토큰 갱신)**: 2 (T026-T027)
**User Story 5 (일일 제한)**: 4 (T028-T031)
**User Story 6 (회원 탈퇴)**: 5 (T032-T036)
**User Story 7 (마이페이지)**: 7 (T037-T041-2 상세조회, 재분석)
**Phase 1 Integration & Polish**: 11 (T042-T050, including T047-T048-3 entity mapping)

---

## Parallel Execution Examples

### Parallel Group 1 (After T013 + T006-1):
- T014 + T018 (US1, US2 DTOs)
- T015 + T020 (AuthService for signup & login)
- T022-1 + T022-2 (EventPublisher + ClientIpUtil, independent of other US2 tasks)
- T047 + T048-1 + T048-2 (Phase 1 entity modifications in parallel)

### Parallel Group 2:
- T016 + T021 (AuthController endpoints: `/api/auth/signup`, `/api/auth/login`)
- T023 (US3 logout logic)
- T048-3 (DB migration for Phase 1 integration)

### Parallel Group 3:
- T028 + T033 (US5, US6 services)
- T038 + T026 (US7 setup, US4 token)
- T049 (Phase 1 coordination)

### Parallel Group 4 (After Phase 1 Integration T047-T048-3):
- T041-1 (GET /api/mypage/analyses/{analysisId} detail view)
- T041-2 (POST /api/mypage/reanalyze/{analysisId} reanalysis)

---

## Format Validation Checklist

✅ All tasks follow `- [ ] [ID] [P?] [Story] Description with file path` format
✅ File paths are absolute (SSAju/src/main/java/ssafy/SSAju/...)
✅ [Story] labels (US1-US7) only on user story phase tasks
✅ [P] parallelizable marker used for independent tasks
✅ Dependencies documented in each phase
✅ MVP scope identified (Phase 2 minimal implementation)
✅ **FIXED**: T026 LogoutRequest DTO 삭제 (Header/Cookie에서 직접 추출)
✅ **FIXED**: T029 TokenRefreshRequest DTO 삭제 (Cookie에서 직접 추출)
✅ **FIXED**: T033, T034 Soft Delete 로직 수정 (@SQLDelete 제거, 명시적 save())
✅ **FIXED**: API 엔드포인트 통일 (`/api` prefix 추가)
  - POST /api/auth/signup, /api/auth/login, /api/auth/logout, /api/auth/refresh
  - DELETE /api/users/me (이전: /auth/user)
  - GET /api/mypage (이미 올바름)
✅ **NEW**: T041-1 & T041-2 (US7 상세조회, 재분석)
✅ **NEW**: T047 split into T047, T048-1, T048-2, T048-3 (Phase 1 entity mapping)
✅ **UPDATED**: T049 Phase 1 coordination (auto user_id mapping)
✅ **IMPROVED**: T006, T022 (failure_reason 필드 + EventPublisher 패턴)
✅ **NEW**: T022-1 & T022-2 (LoginAttemptEvent + ClientIpUtil)
  - **T022-1**: EventPublisher 기반 비동기 로그인 시도 기록 (로그인 응답 지연 방지)
  - **T022-2**: X-Forwarded-For 헤더를 고려한 실제 IP 추출 (운영 환경 대비)
✅ **NEW**: T010-1 (JWT 예외 처리 프레임워크 - JwtExceptionFilter 등 추가)
✅ Total task count: 58 tasks
