# Tasks: 관리자 대시보드 및 모니터링 시스템

**Feature**: `003-admin-dashboard` | **Branch**: `003-admin-dashboard`

**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

---

## Implementation Strategy

**MVP Scope**: Phase 3 (US1 - 대시보드) 완료로 최소 가치 제공
**Incremental Delivery**:
1. Phase 3: 대시보드 (5초 응답 성능 목표)
2. Phase 4: 유저 관리 (검색/필터링)
3. Phase 5: 분석 기록 (JSON 조회 + 일일 제한 초기화)
4. Phase 6: 피드백 관리 (통계 + 연관 링크)

**Parallel Opportunities**:
- Phase 3-4: US1, US2 동시 개발 가능 (독립적인 화면)
- Phase 5-6: US3, US4 동시 개발 가능 (독립적인 데이터)
- DTO, Service, Controller 층은 각 story별로 병렬 개발 가능

**Independent Test Criteria per User Story**:
- **US1**: 대시보드 페이지 로드 후 4개 데이터 섹션 렌더링 확인
- **US2**: 유저 검색 & 상세 프로필 조회, Soft Delete 필터링 확인
- **US3**: 분석 히스토리 조회, JSON 인코딩, 일일 제한 초기화 API
- **US4**: 만족도 통계, 피드백 목록, 분석 결과 연관 링크

---

## Phase 1: Setup & Infrastructure

### Project Structure & Configuration

- [ ] T001 Create admin module package structure in `src/main/java/ssafy/SSAju/admin/` with subdirectories (controller, service, dto, repository)

- [ ] T002 Create Thymeleaf template directory structure in `src/main/resources/templates/admin/` with layout subdirectory

- [ ] T003 Configure Spring Security for admin role-based access control in `src/main/java/ssafy/SSAju/config/AdminSecurityConfig.java`

- [ ] T004 [P] Create test package structure in `src/test/java/ssafy/SSAju/admin/` with controller, service, integration subdirectories

- [ ] T005 [P] Create base application.yml configuration for admin module (logging, pagination defaults) in `src/main/resources/application-admin.yaml`

### DTO & Common Classes

- [ ] T006 [P] Create `DashboardDTO.java` in `src/main/java/ssafy/SSAju/admin/dto/` with fields: totalAnalysis, analysisTypeBreakdown, dailyLimitExhaustedCount, feedbackSummary

- [ ] T007 [P] Create `UserSearchDTO.java` in `src/main/java/ssafy/SSAju/admin/dto/` with fields: id, email, name, joinDate, status, deletedAt, totalAnalysisCount
  - Note: 탈퇴 유저의 email은 이미 마스킹된 형식(deleted_{userId}_{epochSecond}@deleted.local)으로 DB에 저장되므로 maskedEmail 필드 불필요

- [ ] T008 [P] Create `AnalyticsListDTO.java` in `src/main/java/ssafy/SSAju/admin/dto/` with fields: id, userId, analysisType, createdAt

- [ ] T009 [P] Create `AnalyticsDetailDTO.java` in `src/main/java/ssafy/SSAju/admin/dto/` with fields: id, userId, analysisType, jsonData, createdAt

- [ ] T010 [P] Create `FeedbackListDTO.java` in `src/main/java/ssafy/SSAju/admin/dto/` with fields: id, userId, feedbackContent, satisfactionScore, analysisType, createdAt

- [ ] T011 [P] Create `FeedbackStatDTO.java` in `src/main/java/ssafy/SSAju/admin/dto/` with fields: satisCountBySajuType, averageScore, totalFeedbackCount

---

## Phase 2: Foundational & Data Access Layer

### Custom Query Repositories

- [ ] T012 Create custom query repository `AdminAnalyticsQueryRepository.java` in `src/main/java/ssafy/SSAju/admin/repository/` with methods:
  - findAnalyticsByDateAndType (with pagination)
  - findAnalyticsById (with JSON validation)
  - findDailyAnalysisSummary (for dashboard aggregation)

- [ ] T013 Create custom query repository `AdminFeedbackQueryRepository.java` in `src/main/java/ssafy/SSAju/admin/repository/` with methods:
  - findFeedbackByTypeAndDate (with pagination)
  - findFeedbackStatsByAnalysisType (for statistics)
  - findFeedbackWithAnalysis (for linked views)

- [ ] T014 Create custom query repository `AdminUserQueryRepository.java` in `src/main/java/ssafy/SSAju/admin/repository/` with methods:
  - findUsersByFilters (email, name, joinDate, status with pagination)
  - findUserById (with analysis count)
  - findDeletedUsers (Soft Delete filtering)

- [ ] T015 Create custom query repository `AdminDailyUsageQueryRepository.java` in `src/main/java/ssafy/SSAju/admin/repository/` with methods:
  - findUsageByUserAndDate
  - updateUsageCount (for reset/decrement operations)

### Service Layer Base Classes

- [ ] T016 Create base admin service class `AdminBaseService.java` in `src/main/java/ssafy/SSAju/admin/service/` with utility methods (timezone handling, pagination, error handling)

- [ ] T017 Create pagination utility `AdminPaginationUtil.java` in `src/main/java/ssafy/SSAju/admin/service/` with methods for page size validation, offset calculation

---

## Phase 3: User Story 1 - 대시보드 (P1)

**Goal**: 관리자가 서비스 현황을 한눈에 파악할 수 있는 대시보드 제공 (5초 이내 로드)

**Independent Test**:
- 대시보드 페이지 접근 시 4개 데이터 섹션(분석 현황, 사용량 경고, 피드백 요약) 렌더링 확인
- SC-001: 5초 이내 응답
- 각 섹션 데이터 정확성 검증

### Services

- [ ] T018 [US1] Create `AdminDashboardService.java` in `src/main/java/ssafy/SSAju/admin/service/` with methods:
  - getTodaysAnalysisSummary() - returns total count and type breakdown (SAJU, GWANWUN, GUNG_HAP)
  - getDailyLimitExhaustedCount() - returns count of users who used all 3 daily limits
  - getFeedbackSummary() - returns satisfied/unsatisfied ratio and unviewed feedback count
  - getDashboardData() - aggregates all three summaries into DashboardDTO

### Controllers

- [ ] T019 [US1] Create `AdminDashboardController.java` in `src/main/java/ssafy/SSAju/admin/controller/` with endpoints:
  - GET /admin/dashboard (returns JSON + renders Thymeleaf view)
  - GET /admin/api/dashboard (returns JSON only for AJAX refresh)

### Views & Templates

- [ ] T020 [US1] Create Thymeleaf layout base template `admin/layout/admin-base.html` with header, sidebar, footer structure

- [ ] T021 [US1] Create Thymeleaf components:
  - `admin/layout/admin-header.html` - navigation, branding
  - `admin/layout/admin-sidebar.html` - menu items (Dashboard, User Management, Analytics, Feedback)
  - `admin/layout/admin-footer.html` - copyright, version

- [ ] T022 [US1] Create dashboard template `admin/dashboard.html` with sections:
  - Analysis Summary Widget (today's count by type)
  - Daily Limit Warning Widget (users with exhausted quota)
  - Feedback Summary Widget (satisfaction ratio, unviewed count)
  - Manual Refresh Button

### Tests (Optional - JUnit 5 + Mockito)

- [ ] T023 [US1] Create `AdminDashboardServiceTest.java` in `src/test/java/ssafy/SSAju/admin/service/` testing:
  - getTodaysAnalysisSummary() with mock data (Asia/Seoul timezone handling)
  - getDailyLimitExhaustedCount() with edge cases (midnight reset)
  - getFeedbackSummary() with empty/partial data
  - getDashboardData() aggregation

- [ ] T024 [US1] Create `AdminDashboardControllerTest.java` in `src/test/java/ssafy/SSAju/admin/controller/` testing:
  - GET /admin/dashboard response time < 5 seconds
  - JSON structure validation
  - Thymeleaf view rendering

- [ ] T025 [US1] Create integration test `AdminDashboardIntegrationTest.java` in `src/test/java/ssafy/SSAju/admin/integration/` testing:
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

- [ ] T026 [US2] Create `AdminUserService.java` in `src/main/java/ssafy/SSAju/admin/service/` with methods:
  - searchUsers(email, name, joinDateFrom, joinDateTo, status, page, size) - with Soft Delete filtering
  - getUserProfile(userId) - returns UserSearchDTO with totalAnalysisCount
  - Note: getMaskedEmail() 생성 제거. 이유: T007에서 DB 저장 시 이미 마스킹되므로 조회만 필요

### Controllers

- [ ] T027 [US2] Create `AdminUserManagementController.java` in `src/main/java/ssafy/SSAju/admin/controller/` with endpoints:
  - GET /admin/users (search + filter + pagination)
  - GET /admin/users/{id} (detail profile)

### Views & Templates

- [ ] T028 [US2] Create user management template `admin/user-management.html` with:
  - Search form (email, name, joinDate range picker, status dropdown)
  - User list table (pagination, sortable columns)
  - Detail profile modal (basic info, analysis count)

### Tests (Optional)

- [ ] T029 [US2] Create `AdminUserServiceTest.java` testing:
  - searchUsers() with various filter combinations
  - getUserProfile() with analysis count
  - getMaskedEmail() uniqueness across multiple users

- [ ] T030 [US2] Create `AdminUserManagementControllerTest.java` testing:
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

- [ ] T031 [US3] Create `AdminAnalyticsService.java` in `src/main/java/ssafy/SSAju/admin/service/` with methods:
  - getAnalyticsHistory(analysisType, dateFrom, dateTo, page, size) - returns paginated list with latest first
  - getAnalyticsDetail(analysisId) - returns AnalyticsDetailDTO with raw JSON data
  - validateJsonEncoding(jsonData) - checks UTF-8 Korean character rendering

- [ ] T032 [US3] Create `AdminUsageAdjustmentService.java` in `src/main/java/ssafy/SSAju/admin/service/` with methods:
  - resetDailyUsage(userId, date) - sets UsageCount to 0
  - decrementDailyUsage(userId, date, amount) - decreases by amount with validation

### Controllers

- [ ] T033 [US3] Create `AdminAnalyticsController.java` in `src/main/java/ssafy/SSAju/admin/controller/` with endpoints:
  - GET /admin/analytics (list with type/date filtering)
  - GET /admin/analytics/{id} (detail with JSON validation)

- [ ] T034 [US3] Create `AdminUsageAdjustmentController.java` in `src/main/java/ssafy/SSAju/admin/controller/` with endpoints:
  - POST /admin/daily-usages/users/{userId}/adjust (body: {"action": "RESET"} or {"action": "DECREMENT", "amount": N})

### Views & Templates

- [ ] T035 [US3] Create analytics template `admin/analytics-history.html` with:
  - Analytics list table (type filter, date range picker, pagination)
  - Detail view modal (JSON display in code block with syntax highlighting)
  - Usage reset form (action dropdown + amount input + confirm modal)

### Tests (Optional)

- [ ] T036 [US3] Create `AdminAnalyticsServiceTest.java` testing:
  - getAnalyticsHistory() pagination and sorting
  - getAnalyticsDetail() JSON data integrity
  - validateJsonEncoding() with Korean characters

- [ ] T037 [US3] Create `AdminUsageAdjustmentServiceTest.java` testing:
  - resetDailyUsage() with edge cases (already 0)
  - decrementDailyUsage() with validation (cannot go negative)

- [ ] T038 [US3] Create `AdminAnalyticsControllerTest.java` testing:
  - GET /admin/analytics response structure and pagination
  - GET /admin/analytics/{id} detail view with JSON validation

- [ ] T038-B [US3] Create `AdminUsageAdjustmentControllerTest.java` testing:
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

- [ ] T039 [US4] Create `AdminFeedbackService.java` in `src/main/java/ssafy/SSAju/admin/service/` with methods:
  - getFeedbackStats() - aggregates satisfaction scores by analysis type (SAJU, GWANWUN, GUNG_HAP)
  - getFeedbackList(analysisType, page, size) - returns paginated feedback with filtering
  - getFeedbackWithAnalysis(feedbackId) - returns linked analysis result for that feedback

### Controllers

- [ ] T040 [US4] Create `AdminFeedbackController.java` in `src/main/java/ssafy/SSAju/admin/controller/` with endpoints:
  - GET /admin/feedback (list with type filtering)
  - GET /admin/feedback/stats (satisfaction statistics)
  - GET /admin/feedback/{id}/analysis/{analysisId} (linked view)

### Views & Templates

- [ ] T041 [US4] Create feedback template `admin/feedback-management.html` with:
  - Satisfaction stats section (average score by type, distribution chart)
  - Feedback list table (type filter, pagination, content preview)
  - Analysis result linked view (modal or new tab)

### Tests (Optional)

- [ ] T042 [US4] Create `AdminFeedbackServiceTest.java` testing:
  - getFeedbackStats() calculation accuracy (average, distribution)
  - getFeedbackList() filtering and pagination
  - getFeedbackWithAnalysis() data linking correctness

- [ ] T043 [US4] Create `AdminFeedbackControllerTest.java` testing:
  - GET /admin/feedback/stats response structure
  - GET /admin/feedback/{id}/analysis/{analysisId} linked data validation

---

## Phase 7: Polish & Cross-Cutting Concerns

### Error Handling & Validation

- [ ] T044 Create global exception handler `AdminExceptionHandler.java` in `src/main/java/ssafy/SSAju/admin/config/` for:
  - 404 (user/analysis not found)
  - 400 (invalid filter/page parameters)
  - 500 (database/service errors)
  - Proper JSON error responses

- [ ] T045 Create input validation utilities in `src/main/java/ssafy/SSAju/admin/validation/`:
  - DateRangeValidator.java (from <= to)
  - PaginationValidator.java (page >= 0, size > 0)
  - FilterValidator.java (allowed enum values)

### Logging & Monitoring

- [ ] T046 [P] Add structured logging to all admin services:
  - Log incoming requests (user, filters, page)
  - Log query execution time (performance monitoring)
  - Log errors with context

- [ ] T047 [P] Apply existing `@AuditLog` annotation to sensitive admin service methods for tracking:
  - Attach `@AuditLog` to `AdminUsageAdjustmentService.resetDailyUsage()` and `decrementDailyUsage()` methods
  - Attach `@AuditLog` to critical admin search/filter operations (reuse existing AuditLoggingAspect - no new utility needed)
  - Logs include: action name, userId, status (SUCCESS|FAILURE), execution time
  - Personal information (email, password) automatically excluded by existing aspect

### Performance & Caching

- [ ] T048 Add query optimization:
  - Verify indexes on SajuAnalysis (CreatedAt, AnalysisType, UserId)
  - Verify indexes on User (DeletedAt, Status)
  - Verify indexes on UserSatisfactionFeedback (CreatedAt, AnalysisType)

- [ ] T049 [P] Implement result caching (optional, v1) for dashboard statistics:
  - Cache dashboard data for 1 minute
  - Invalidate on new analysis creation

### Documentation & Testing

- [ ] T050 Create integration test script `AdminDashboardFullIntegrationTest.java` covering:
  - Complete user flows for each story
  - Data consistency validation
  - Performance benchmarks (5s, 2s, 3s targets)

- [ ] T051 Create Postman collection or curl examples for all admin APIs

- [ ] T052 Create admin page user guide in `docs/admin-guide.md`:
  - Dashboard interpretation
  - User management best practices
  - Troubleshooting common issues

---

## Task Summary

| Phase | Tasks | Focus |
|-------|-------|-------|
| **Phase 1** | T001-T011 | Setup, DTOs, Configuration |
| **Phase 2** | T012-T017 | Data Access, Query Repositories |
| **Phase 3 (US1)** | T018-T025 | Dashboard (P1) |
| **Phase 4 (US2)** | T026-T030 | User Management (P1) |
| **Phase 5 (US3)** | T031-T038 | Analytics & Usage (P2) |
| **Phase 6 (US4)** | T039-T043 | Feedback & Stats (P2) |
| **Phase 7** | T044-T052 | Polish, Logging, Docs |

**Total Tasks**: 52 (31 Implementation + 21 Optional Tests)

---

## Parallel Execution Roadmap

```text
Phase 1 (Setup) [Sequential - must complete first]
    ↓
Phase 2 (Foundational) [Sequential - must complete first]
    ↓
┌─────────────────────┬─────────────────────┐
│  Phase 3 (US1)      │  Phase 4 (US2)      │  ← Parallel (P1 stories)
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

## MVP Checklist (Phase 3 Only)

- [ ] T018-T025 Admin Dashboard Service, Controller, Views, Tests
- [ ] T012-T014 Analytics Query Repository
- [ ] Performance validation: Dashboard load time < 5 seconds
- [ ] Independent test passing: Dashboard displays all 4 widgets correctly

---

**Status**: Ready for implementation
**Next**: Begin Phase 1 tasks (T001-T011) or use this roadmap for team parallel work
