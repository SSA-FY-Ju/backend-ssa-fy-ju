# Tasks: 관리자 대시보드 및 모니터링 시스템

**Feature**: `003-admin-dashboard` | **Branch**: `003-admin-dashboard`

**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

---

## Implementation Strategy

**MVP Scope**: Phase 2 (US0 - 관리자 로그인) 완료로 기초 인증 마련 + Phase 3 (US1 - 대시보드) 완료로 관리자 운영 시작 가능
**Incremental Delivery**:
1. Phase 2: 기반 인프라 + 관리자 로그인 (필수 선행)
2. Phase 3: 대시보드 (5초 응답 성능 목표)
3. Phase 4: 유저 관리 (검색/필터링)
4. Phase 5: 분석 기록 (JSON 조회 + 일일 제한 초기화)
5. Phase 6: 피드백 관리 (통계 + 연관 링크)

**Parallel Opportunities**:
- Phase 3-4: US1, US2 동시 개발 가능 (독립적인 화면, US0 로그인 완료 후)
- Phase 5-6: US3, US4 동시 개발 가능 (독립적인 데이터)
- DTO, Service, Controller 층은 각 story별로 병렬 개발 가능

**Independent Test Criteria per User Story**:
- **US0**: 관리자 로그인 폼, ADMIN/USER 권한 검증, 세션 관리 확인
- **US1**: 대시보드 페이지 로드 후 4개 데이터 섹션 렌더링 확인
- **US2**: 유저 검색 & 상세 프로필 조회, Soft Delete 필터링 확인
- **US3**: 분석 히스토리 조회, JSON 인코딩, 일일 제한 초기화 API
- **US4**: 만족도 통계, 피드백 목록, 분석 결과 연관 링크

---

## Phase 1: Setup & Infrastructure

### Project Structure & Configuration

- [x] T001 Create admin module package structure in `src/main/java/ssafy/SSAju/admin/` with subdirectories (controller, service, dto, repository, config)

- [x] T002 Create Thymeleaf template directory structure in `src/main/resources/templates/admin/` with layout subdirectory

- [x] T003 [P] Create test package structure in `src/test/java/ssafy/SSAju/admin/` with controller, service, integration subdirectories

- [x] T004 [P] Create base application-admin.yaml configuration for admin module (logging, pagination defaults) in `src/main/resources/`

### DTO & Common Classes

- [x] T005 [P] Create `DashboardDTO.java` in `src/main/java/ssafy/SSAju/admin/dto/` with fields: totalAnalysis, analysisTypeBreakdown, dailyLimitExhaustedCount, feedbackSummary

- [x] T006 [P] Create `UserSearchDTO.java` in `src/main/java/ssafy/SSAju/admin/dto/` with fields: id, email, name, joinDate, status, deletedAt, totalAnalysisCount

- [x] T007 [P] Create `AnalyticsListDTO.java` in `src/main/java/ssafy/SSAju/admin/dto/` with fields: id, userId, analysisType, createdAt

- [x] T008 [P] Create `AnalyticsDetailDTO.java` in `src/main/java/ssafy/SSAju/admin/dto/` with fields: id, userId, analysisType, jsonData, createdAt

- [x] T009 [P] Create `FeedbackListDTO.java` in `src/main/java/ssafy/SSAju/admin/dto/` with fields: id, userId, feedbackContent, satisfactionScore, analysisType, createdAt

- [x] T010 [P] Create `FeedbackStatDTO.java` in `src/main/java/ssafy/SSAju/admin/dto/` with fields: satisCountBySajuType, averageScore, totalFeedbackCount

- [x] T011 [P] Create `AdminLoginRequestDTO.java` and `AdminLoginResponseDTO.java` in `src/main/java/ssafy/SSAju/admin/dto/` with JWT token fields

---

## Phase 2: Foundational & 관리자 로그인 (US0 - P0)

### Custom Query Repositories

- [x] T012 Create custom query repository `AdminAnalyticsQueryRepository.java` in `src/main/java/ssafy/SSAju/admin/repository/` with methods:
  - findAnalyticsByDateAndType (with pagination)
  - findAnalyticsById (with JSON validation)
  - findDailyAnalysisSummary (for dashboard aggregation)

- [x] T013 Create custom query repository `AdminFeedbackQueryRepository.java` in `src/main/java/ssafy/SSAju/admin/repository/` with methods:
  - findFeedbackByTypeAndDate (with pagination)
  - findFeedbackStatsByAnalysisType (for statistics)
  - findFeedbackWithAnalysis (for linked views)

- [x] T014 Create custom query repository `AdminUserQueryRepository.java` in `src/main/java/ssafy/SSAju/admin/repository/` with methods:
  - findUsersByFilters (email, name, joinDate, status with pagination)
  - findUserById (with analysis count)
  - findDeletedUsers (Soft Delete filtering)

- [x] T015 Create custom query repository `AdminDailyUsageQueryRepository.java` in `src/main/java/ssafy/SSAju/admin/repository/` with methods:
  - findUsageByUserAndDate
  - updateUsageCount (for reset/decrement operations)

### Service Layer Base Classes

- [x] T016 Create base admin service class `AdminBaseService.java` in `src/main/java/ssafy/SSAju/admin/service/` with utility methods (timezone handling, pagination, error handling)

- [x] T017 Create pagination utility `AdminPaginationUtil.java` in `src/main/java/ssafy/SSAju/admin/service/` with methods for page size validation, offset calculation

### User Story 0: 관리자 로그인 (Priority: P0) ⚠️ 필수 선행

**Goal**: 관리자가 ADMIN 권한으로 로그인하여 JWT AccessToken을 발급받고 모든 관리자 페이지에 접근 가능하도록 함

**Independent Test**:
- 관리자 로그인 폼 렌더링 확인
- ADMIN 권한 사용자 로그인 시 AccessToken 발급
- USER 권한 사용자 로그인 시도 시 에러 메시지 표시
- 미인증 사용자의 /admin 접근 시 로그인 페이지로 리다이렉트
- 로그아웃 시 세션 종료 확인

### Services

**의도 변경**: 독립적인 토큰 생성 서비스 제거 → 기존 AuthService 재사용 (DRY 원칙)

- [x] **T018-A** [US0] Create `AdminAuthenticationService.java` in `src/main/java/ssafy/SSAju/admin/service/` with **single method**:
  - `validateAdminCredentials(email, password)` 
    - ROLE=ADMIN 사용자만 검증
    - DB에서 사용자 조회 후 role 확인
    - 비관리자 사용자는 예외 발생: `AUTH-003 ("접근 권한이 없습니다.")`
  - **주의**: JWT 토큰 생성/검증/무효화는 기존 AuthService 재사용

- [x] **T018-B** [US0] Modify existing `AuthService.login()`:
  - admin 로그인 요청도 동일하게 처리
  - AdminAuthenticationService.validateAdminCredentials() 먼저 호출해서 ROLE_ADMIN 여부 검증
  - 검증 통과 후 기존 로직으로 JWT 토큰 생성 (AccessToken + RefreshToken)
  - ⚠️ 중복 제거: JWT 생성/검증 로직 새로 구현 금지, RefreshTokenRepository 재사용

### Controllers

- [x] **T019** [US0] Create `AdminLoginController.java` in `src/main/java/ssafy/SSAju/admin/controller/` with endpoints:
  - `GET /admin/login` → render login form (Thymeleaf)
  - `POST /admin/login` → call AdminAuthenticationService.validateAdminCredentials() 후 AuthService.login() 재사용
    - 성공: AccessToken + RefreshToken 발급, 프론트엔드는 /admin/dashboard로 리다이렉트 처리
    - 실패 (USER 권한): AUTH-003 에러 응답 및 로그인 폼 재렌더링
    - 실패 (자격증명 오류): AUTH-002 에러 응답 및 로그인 폼 재렌더링
  - `POST /admin/logout` → AuthService.logout() 재사용 (RefreshToken revoke)
    - RefreshToken 삭제 처리
    - 프론트엔드에서 AccessToken 삭제 (Stateless JWT)
    - 프론트엔드에서 /admin/login으로 리다이렉트

### Views & Templates

- [x] T020 [US0] Create Thymeleaf layout base template `admin/layout/admin-base.html` with header, sidebar, footer structure

- [x] T021 [US0] Create Thymeleaf admin login template `admin/login.html` with:
  - Email/password input form
  - Error message display (invalid credentials, role denied)
  - Submit button with loading state
  - Responsive design

### Spring Security Configuration

**주의**: Stateless JWT + SSR 리다이렉트 혼합 구조

- [x] **T022** [US0] Create/Update `AdminSecurityConfig.java` in `src/main/java/ssafy/SSAju/config/` (@Order(0) 으로 전역 SecurityConfig 보다 먼저 적용):
  - `/admin/**` 경로에만 적용되는 전용 SecurityFilterChain
  - @PreAuthorize("hasRole('ADMIN')") on all /admin/** endpoints
  - Configure login page: `/admin/login`
  - Configure logout handler (⚠️ 수정):
    - ❌ "invalidate session" 제거 (JWT는 Stateless)
    - ✅ "RefreshToken revoke 처리 + /admin/login으로 리다이렉트"
    - RefreshToken을 DB에서 삭제
    - 프론트엔드에서 AccessToken 삭제 처리 (Stateless이므로 서버 불가)
  - Custom AuthenticationEntryPoint: 비인증 요청 처리
  - Custom AccessDeniedHandler: 비관리자 요청 처리 (아래 T023 참고)

- [x] **T023** [US0] Create `AdminAuthenticationEntryPoint.java` + `AdminAccessDeniedHandler.java` in `src/main/java/ssafy/SSAju/admin/config/`:

  **AdminAuthenticationEntryPoint.java** (비인증 요청 401):
  ```java
  public void commence(HttpServletRequest request, HttpServletResponse response, 
                       AuthenticationException authException) throws IOException {
      if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
          // AJAX 요청 → JSON 에러 응답
          response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
          response.setContentType("application/json;charset=UTF-8");
          response.getWriter().write("{\"code\":\"AUTH-001\",\"message\":\"인증이 필요합니다.\"}");
      } else {
          // 일반 브라우저 → HTML 리다이렉트
          response.sendRedirect("/admin/login");
      }
  }
  ```

  **AdminAccessDeniedHandler.java** (비관리자 요청 403):
  ```java
  public void handle(HttpServletRequest request, HttpServletResponse response,
                     AccessDeniedException accessDeniedException) throws IOException {
      if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
          // AJAX 요청 → JSON 에러 응답
          response.setStatus(HttpServletResponse.SC_FORBIDDEN);
          response.setContentType("application/json;charset=UTF-8");
          response.getWriter().write("{\"code\":\"AUTH-003\",\"message\":\"접근 권한이 없습니다.\"}");
      } else {
          // 일반 브라우저 → HTML 리다이렉트
          response.sendRedirect("/admin/login");
      }
  }
  ```

### Tests (Optional - JUnit 5 + Mockito)

- [x] **T024** [US0] Create `AdminAuthenticationServiceTest.java` in `src/test/java/ssafy/SSAju/admin/service/` testing:
  - validateAdminCredentials() with ADMIN role → 성공
  - validateAdminCredentials() with USER role → AUTH-003 예외 발생
  - validateAdminCredentials() with invalid credentials → 적절한 예외 발생
  - ⚠️ JWT 생성/검증 테스트 제거 (AuthService 테스트 대신)

- [x] **T025** [US0] Create `AdminLoginControllerTest.java` in `src/test/java/ssafy/SSAju/admin/controller/` testing:
  - GET /admin/login form rendering (Thymeleaf 확인)
  - POST /admin/login with valid ADMIN credentials → AuthService.login() 호출, token issued
  - POST /admin/login with USER credentials → AUTH-003 에러 응답 + 폼 재렌더링
  - POST /admin/logout → AuthService.logout() 호출, RefreshToken 삭제 확인
  - ⚠️ JWT 토큰 생성/검증은 T018-B의 AuthService.login() 테스트로 충분

---

## Phase 3: User Story 1 - 대시보드 (P1)

**Goal**: 관리자가 서비스 현황을 한눈에 파악할 수 있는 대시보드 제공 (5초 이내 로드)

**Independent Test**:
- 대시보드 페이지 접근 시 4개 데이터 섹션(분석 현황, 사용량 경고, 피드백 요약) 렌더링 확인
- SC-001: 5초 이내 응답
- 각 섹션 데이터 정확성 검증

### Services

- [x] T026 [US1] Create `AdminDashboardService.java` in `src/main/java/ssafy/SSAju/admin/service/` with methods:
  - getTodaysAnalysisSummary() - returns total count and type breakdown (SAJU, GWANWUN, GUNG_HAP)
  - getDailyLimitExhaustedCount() - returns count of users who used all 3 daily limits
  - getFeedbackSummary() - returns satisfied/unsatisfied ratio and unviewed feedback count
  - getDashboardData() - aggregates all three summaries into DashboardDTO

### Controllers

- [x] T027 [US1] Create `AdminDashboardController.java` in `src/main/java/ssafy/SSAju/admin/controller/` with endpoints:
  - GET /admin/dashboard (returns JSON + renders Thymeleaf view)
  - GET /admin/api/dashboard (returns JSON only for AJAX refresh)

### Views & Templates

- [x] T028 [US1] Create Thymeleaf components:
  - `admin/layout/admin-header.html` - navigation, branding
  - `admin/layout/admin-sidebar.html` - menu items (Dashboard, User Management, Analytics, Feedback)
  - `admin/layout/admin-footer.html` - copyright, version

- [x] T029 [US1] Create dashboard template `admin/dashboard.html` with sections:
  - Analysis Summary Widget (today's count by type)
  - Daily Limit Warning Widget (users with exhausted quota)
  - Feedback Summary Widget (satisfaction ratio, unviewed count)
  - Manual Refresh Button

### Tests (Optional - JUnit 5 + Mockito)

- [x] T030 [US1] Create `AdminDashboardServiceTest.java` in `src/test/java/ssafy/SSAju/admin/service/` testing:
  - getTodaysAnalysisSummary() with mock data (Asia/Seoul timezone handling)
  - getDailyLimitExhaustedCount() with edge cases (midnight reset)
  - getFeedbackSummary() with empty/partial data
  - getDashboardData() aggregation

- [x] T031 [US1] Create `AdminDashboardControllerTest.java` in `src/test/java/ssafy/SSAju/admin/controller/` testing:
  - GET /admin/dashboard response time < 5 seconds
  - JSON structure validation
  - Thymeleaf view rendering

- [x] T032 [US1] Create integration test `AdminDashboardIntegrationTest.java` in `src/test/java/ssafy/SSAju/admin/integration/` testing:
  - End-to-end dashboard flow with real DB mock data
  - Data consistency across services

---

## Phase 4: User Story 2 - 유저 관리 (P1)

**Goal**: 관리자가 유저를 검색, 필터링, 상세 조회할 수 있는 인터페이스 제공

**Independent Test**:
- 유저 검색(이메일, 이름, 가입일, 상태 필터)이 정확하게 작동
- 상세 프로필 조회 시 분석 횟수 표시
- Soft Delete 탈퇴 유저 필터링 및 이메일 마스킹 확인

### Services

- [x] T033 [US2] Create `AdminUserService.java` in `src/main/java/ssafy/SSAju/admin/service/` with methods:
  - `searchUsers(email, name, joinDateFrom, joinDateTo, status, page, size)` - with Soft Delete filtering
  - `getUserProfile(userId)` - returns UserSearchDTO with totalAnalysisCount
  
  **⚠️ 마스킹 이메일 주의**:
  - ❌ getMaskedEmail() 메서드 생성 금지 (중복)
  - ✅ DB에서 조회 시 탈퇴 사용자의 이메일은 이미 마스킹된 형식
    (예: deleted_123_1686326400@deleted.local) 으로 저장됨
  - ✅ 조회만 하면 됨, 생성 로직은 User.deleteUser() (또는 유사 메서드)에서 이미 처리

### Controllers

- [x] T034 [US2] Create `AdminUserManagementController.java` in `src/main/java/ssafy/SSAju/admin/controller/` with endpoints:
  - GET /admin/users (search + filter + pagination)
  - GET /admin/users/{id} (detail profile)

### Views & Templates

- [x] T035 [US2] Create user management template `admin/user-management.html` with:
  - Search form (email, name, joinDate range picker, status dropdown)
  - User list table (pagination, sortable columns)
  - Detail profile modal (basic info, analysis count)

### Tests (Optional)

- [x] T036 [US2] Create `AdminUserServiceTest.java` testing:
  - searchUsers() with various filter combinations
  - getUserProfile() with analysis count
  - Soft Delete filtering correctness

- [x] T037 [US2] Create `AdminUserManagementControllerTest.java` testing:
  - GET /admin/users response time < 2 seconds (1000 records)
  - Soft Delete filtering correctness
  - Pagination functionality

---

## Phase 5: User Story 3 - 분석 기록 (P2)

**Goal**: 관리자가 전체 분석 히스토리를 조회, 데이터 검증, 일일 제한 수동 조정

**Independent Test**:
- 분석 기록 조회 및 페이지네이션 정상 작동
- JSON 데이터 원문 확인 (한글 인코딩 검증)
- 일일 제한 초기화 API (리셋/차감) 정상 작동

### Services

- [x] T038 [US3] Create `AdminAnalyticsService.java` in `src/main/java/ssafy/SSAju/admin/service/` with methods:
  - getAnalyticsHistory(analysisType, dateFrom, dateTo, page, size) - returns paginated list with latest first
  - getAnalyticsDetail(Long id, String analysisType) - returns AnalyticsDetailDTO with raw JSON data
  - (validateJsonEncoding 미구현)

- [x] T039 [US3] Create `AdminUsageAdjustmentService.java` in `src/main/java/ssafy/SSAju/admin/service/` with methods:
  - adjustDailyUsage(Long userId, UsageAdjustmentRequestDTO request) - 단일 진입점, RESET/DECREMENT 처리
  - (resetDailyUsage / decrementDailyUsage 는 AdminDailyUsageQueryRepository 의 메서드)

### Controllers

- [x] T040 [US3] Create `AdminAnalyticsController.java` in `src/main/java/ssafy/SSAju/admin/controller/` with endpoints:
  - GET /admin/analytics (list with type/date filtering)
  - GET /admin/analytics/{id}?type={analysisType} (detail - @RequestParam String type 필수)

- [x] T041 [US3] Create `AdminUsageAdjustmentController.java` in `src/main/java/ssafy/SSAju/admin/controller/` with endpoints:
  - POST /admin/daily-usages/users/{userId}/adjust (body: {"action": "RESET"} or {"action": "DECREMENT", "amount": N})

### Views & Templates

- [x] T042 [US3] Create analytics template `admin/analytics-history.html` with:
  - Analytics list table (type filter, date range picker, pagination)
  - Detail view modal (JSON display in code block with syntax highlighting)
  - Usage reset form (action 라디오 버튼 RESET/DECREMENT + amount input + confirm modal)

### Tests (Optional)

- [x] T043 [US3] Create `AdminAnalyticsServiceTest.java` testing:
  - getAnalyticsHistory() pagination and sorting
  - getAnalyticsDetail() JSON data integrity
  - validateJsonEncoding() with Korean characters

- [x] T044 [US3] Create `AdminUsageAdjustmentServiceTest.java` testing:
  - resetDailyUsage() with edge cases (already 0)
  - decrementDailyUsage() with validation (cannot go negative)

- [x] T045 [US3] Create `AdminAnalyticsControllerTest.java` testing:
  - GET /admin/analytics response structure and pagination
  - GET /admin/analytics/{id} detail view with JSON validation

- [x] T046 [US3] Create `AdminUsageAdjustmentControllerTest.java` testing:
  - POST /admin/daily-usages/users/{userId}/adjust with RESET action
  - POST /admin/daily-usages/users/{userId}/adjust with DECREMENT action and validation

---

## Phase 6: User Story 4 - 피드백 관리 (P2)

**Goal**: 관리자가 유저 피드백을 수집, 분석하고 실제 분석 결과와 연결

**Independent Test**:
- 만족도 통계(평균, 분포) 정확하게 계산
- 피드백 목록 조회 및 필터링 정상 작동
- 분석 결과 연관 링크 정상 작동

### Services

- [x] T047 [US4] Create `AdminFeedbackService.java` in `src/main/java/ssafy/SSAju/admin/service/` with methods:
  - getFeedbackStats() - aggregates satisfaction scores by analysis type (SAJU, GWANWUN, GUNG_HAP)
  - getFeedbackList(analysisType, page, size) - returns paginated feedback with filtering
  - getFeedbackWithAnalysis(feedbackId) - returns linked analysis result for that feedback

### Controllers

- [x] T048 [US4] Create `AdminFeedbackController.java` in `src/main/java/ssafy/SSAju/admin/controller/` with endpoints:
  - GET /admin/feedback (list with type filtering)
  - GET /admin/feedback/stats (satisfaction statistics)
  - GET /admin/feedback/{id}/analysis/{analysisId} (linked view)

### Views & Templates

- [x] T049 [US4] Create feedback template `admin/feedback-management.html` with:
  - Satisfaction stats section (average score by type, distribution chart)
  - Feedback list table (type filter, pagination, content preview)
  - Analysis result linked view (modal or new tab)

### Tests (Optional)

- [x] T050 [US4] Create `AdminFeedbackServiceTest.java` testing:
  - getFeedbackStats() calculation accuracy (average, distribution)
  - getFeedbackList() filtering and pagination
  - getFeedbackWithAnalysis() data linking correctness

- [x] T051 [US4] Create `AdminFeedbackControllerTest.java` testing:
  - GET /admin/feedback/stats response structure
  - GET /admin/feedback/{id}/analysis/{analysisId} linked data validation

---

## Phase 7: Polish & Cross-Cutting Concerns

### Error Handling & Validation

- [ ] T052 Create global exception handler `AdminExceptionHandler.java` in `src/main/java/ssafy/SSAju/admin/config/` for:
  - 404 (user/analysis not found)
  - 400 (invalid filter/page parameters)
  - 500 (database/service errors)
  - Proper JSON error responses

- [ ] T053 Create input validation utilities in `src/main/java/ssafy/SSAju/admin/validation/`:
  - DateRangeValidator.java (from <= to)
  - PaginationValidator.java (page >= 0, size > 0)
  - FilterValidator.java (allowed enum values)

### Logging & Monitoring

- [ ] T054 [P] Add structured logging to all admin services:
  - Log incoming requests (user, filters, page)
  - Log query execution time (performance monitoring)
  - Log errors with context

- [ ] T055 [P] Apply existing `@AuditLog` annotation to sensitive admin service methods for tracking:
  - Attach `@AuditLog` to `AdminUsageAdjustmentService.resetDailyUsage()` and `decrementDailyUsage()` methods
  - Attach `@AuditLog` to admin login/logout operations
  - Logs include: action name, userId, status (SUCCESS|FAILURE), execution time

### Performance & Caching

- [ ] T056 Add query optimization:
  - Verify indexes on SajuAnalysis (CreatedAt, AnalysisType, UserId)
  - Verify indexes on User (DeletedAt, Status)
  - Verify indexes on UserSatisfactionFeedback (CreatedAt, AnalysisType)

- [ ] T057 [P] Implement result caching (optional, v1) for dashboard statistics:
  - Cache dashboard data for 1 minute
  - Invalidate on new analysis creation

### Documentation & Testing

- [ ] T058 Create integration test script `AdminDashboardFullIntegrationTest.java` covering:
  - Complete user flows for each story (including US0 login)
  - Data consistency validation
  - Performance benchmarks (5s, 2s, 3s targets)

- [ ] T059 Create Postman collection or curl examples for all admin APIs including login

- [ ] T060 Create admin page user guide in `docs/admin-guide.md`:
  - Admin login flow
  - Dashboard interpretation
  - User management best practices
  - Troubleshooting common issues

---

## Task Summary

| Phase | Tasks | Focus |
|-------|-------|-------|
| **Phase 1** | T001-T011 | Setup, DTOs, Configuration |
| **Phase 2** | T012-T025 | Data Access, Query Repositories, 관리자 로그인 (US0) |
| **Phase 3 (US1)** | T026-T032 | Dashboard (P1) |
| **Phase 4 (US2)** | T033-T037 | User Management (P1) |
| **Phase 5 (US3)** | T038-T046 | Analytics & Usage (P2) |
| **Phase 6 (US4)** | T047-T051 | Feedback & Stats (P2) |
| **Phase 7** | T052-T060 | Polish, Logging, Docs |

**Total Tasks**: 60 (40 Implementation + 20 Optional Tests)

---

## Parallel Execution Roadmap

```text
Phase 1 (Setup) [Sequential - must complete first]
    ↓
Phase 2 (Foundational + US0 Login) [Sequential - must complete first]
    ↓
┌─────────────────────┬─────────────────────┐
│  Phase 3 (US1)      │  Phase 4 (US2)      │  ← Parallel (P1 stories, after US0 login)
│  Dashborad          │  User Management    │
└─────────────────────┴─────────────────────┘
    ↓
┌─────────────────────┬─────────────────────┐
│  Phase 5 (US3)      │  Phase 6 (US4)      │  ← Parallel (P2 stories)
│  Analytics & Usage  │  Feedback & Stats   │
└─────────────────────┴─────────────────────┘
    ↓
Phase 7 (Polish) [Final - performance, docs, testing]
```

---

## MVP Checklist (US0 + Phase 3)

- [x] T018-T025 Admin Authentication Service, Controller, Views, Tests
- [x] T026-T032 Admin Dashboard Service, Controller, Views, Tests
- [x] T012-T014 Analytics Query Repository
- [x] Performance validation: Dashboard load time < 5 seconds
- [x] Independent test passing: Admin login works + Dashboard displays all 4 widgets correctly

---

**Status**: Ready for implementation
**Next**: Begin Phase 1 tasks (T001-T011) followed by Phase 2 including US0 Admin Login
